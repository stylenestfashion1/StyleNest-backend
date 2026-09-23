package com.stylenest.stylenest_backend.service.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.dto.discount.DiscountConfigResponse;
import com.stylenest.stylenest_backend.dto.discount.DiscountConfigUpdateRequest;
import com.stylenest.stylenest_backend.dto.discount.DiscountRangeResponse;
import com.stylenest.stylenest_backend.dto.discount.DiscountRangeUpdateRequest;
import com.stylenest.stylenest_backend.dto.discount.DiscountSlotRequest;
import com.stylenest.stylenest_backend.entity.DiscountRangeConfig;
import com.stylenest.stylenest_backend.entity.DiscountSlot;
import com.stylenest.stylenest_backend.exception.BadRequestException;
import com.stylenest.stylenest_backend.mapper.DiscountMapper;
import com.stylenest.stylenest_backend.repository.DiscountRangeConfigRepository;
import com.stylenest.stylenest_backend.repository.DiscountSlotRepository;
import com.stylenest.stylenest_backend.service.DiscountConfigService;

import lombok.RequiredArgsConstructor;

/**
 * Owns two DELIBERATELY INDEPENDENT things -- do not let their methods
 * read or write each other's state:
 *
 *  1. The REAL discount configuration (DiscountSlot) -- Admin -> Rewards ->
 *     Custom Discount. Slots are fully dynamic (MIN_SLOTS..MAX_SLOTS), each
 *     bounded only by ABSOLUTE_FLOOR/ABSOLUTE_CEILING (1-100%), and this is
 *     the ONLY thing that drives weighted-random selection
 *     (DiscountOfferServiceImpl#selectWeightedDiscount). See getConfig/updateConfig.
 *
 *  2. The QR Display Range (DiscountRangeConfig) -- Admin -> Rewards ->
 *     Show Discount QR. Purely cosmetic text shown on the printed poster
 *     (e.g. "15% - 40% OFF"). It has NO effect on what discount a customer
 *     can actually receive, and is bounded only by ABSOLUTE_FLOOR/CEILING.
 *     See getDisplayRange/updateRange.
 *
 * Changing one must never change the other, and neither validates against
 * the other's current value. Existing generated discounts (DiscountOffer)
 * are snapshots and are never retroactively touched by either.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DiscountConfigServiceImpl implements DiscountConfigService {

    public static final int MIN_SLOTS = 2;
    public static final int MAX_SLOTS = 10;

    // Hard, non-negotiable safety net shared by both settings -- no
    // configuration, typo, or bug can ever push either one outside 1-100%.
    public static final int ABSOLUTE_FLOOR = 1;
    public static final int ABSOLUTE_CEILING = 100;

    // Defaults on first boot -- see ensureDefaultSeeded. Coincidentally the
    // same numbers today, but stored and managed completely separately.
    private static final int DEFAULT_MIN_DISCOUNT = 15;
    private static final int DEFAULT_MAX_DISCOUNT = 25;

    private final DiscountSlotRepository discountSlotRepository;
    private final DiscountRangeConfigRepository discountRangeConfigRepository;
    private final DiscountMapper discountMapper;

    @Override
    public void ensureDefaultSeeded() {

        if (discountRangeConfigRepository.count() == 0) {
            discountRangeConfigRepository.save(DiscountRangeConfig.builder()
                    .id(DiscountRangeConfig.SINGLETON_ID)
                    .minDiscountPercentage(DEFAULT_MIN_DISCOUNT)
                    .maxDiscountPercentage(DEFAULT_MAX_DISCOUNT)
                    .build());
        }

        if (discountSlotRepository.count() > 0) {
            return;
        }

        // Initial default slots -- see class javadoc. Only ever created
        // once, on an empty table, so a restart never resets an admin's
        // customized configuration back to these values.
        discountSlotRepository.saveAll(List.of(
                DiscountSlot.builder().discountPercentage(15).probabilityPercentage(40).build(),
                DiscountSlot.builder().discountPercentage(20).probabilityPercentage(35).build(),
                DiscountSlot.builder().discountPercentage(25).probabilityPercentage(25).build()
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public DiscountConfigResponse getConfig() {

        return discountMapper.toConfigResponse(discountSlotRepository.findAllByOrderByDiscountPercentageAsc());
    }

    @Override
    public DiscountConfigResponse updateConfig(DiscountConfigUpdateRequest request) {

        List<DiscountSlotRequest> slots = request.getSlots();

        if (slots.size() < MIN_SLOTS || slots.size() > MAX_SLOTS) {
            throw new BadRequestException("You must configure between " + MIN_SLOTS + " and " + MAX_SLOTS + " discount slots.");
        }

        // Each slot's own bean validation (@Min/@Max on DiscountSlotRequest)
        // already enforces ABSOLUTE_FLOOR/ABSOLUTE_CEILING (1-100%) -- there
        // is deliberately no other gate here, and this method never reads
        // or writes DiscountRangeConfig (see class javadoc).

        Set<Integer> distinctDiscounts = new HashSet<>();
        for (DiscountSlotRequest slot : slots) {
            if (!distinctDiscounts.add(slot.getDiscountPercentage())) {
                throw new BadRequestException("Discount values must be unique.");
            }
        }

        int totalProbability = slots.stream().mapToInt(DiscountSlotRequest::getProbabilityPercentage).sum();

        if (totalProbability != 100) {
            throw new BadRequestException("Total probability must equal 100%. Currently " + totalProbability + "%.");
        }

        // Slots carry no identity beyond their discount value, and the set
        // can grow/shrink freely -- replacing the whole table in one
        // transaction is simpler and just as safe as diffing rows, since
        // nothing else references discount_slots by id (a generated
        // discount snapshots its own percentage, see DiscountOffer).
        discountSlotRepository.deleteAllInBatch();

        List<DiscountSlot> newSlots = slots.stream()
                .map(s -> DiscountSlot.builder()
                        .discountPercentage(s.getDiscountPercentage())
                        .probabilityPercentage(s.getProbabilityPercentage())
                        .build())
                .toList();

        discountSlotRepository.saveAll(newSlots);

        return getConfig();
    }

    @Override
    @Transactional(readOnly = true)
    public DiscountRangeResponse getDisplayRange() {
        return discountMapper.toRangeResponse(currentRange());
    }

    @Override
    public DiscountRangeResponse updateRange(DiscountRangeUpdateRequest request) {

        // Bean validation on the DTO already enforces ABSOLUTE_FLOOR/CEILING
        // (1-100) on each field individually; this enforces the
        // relationship between them. Deliberately does NOT read
        // DiscountSlot at all -- see class javadoc.
        if (request.getMinDiscountPercentage() >= request.getMaxDiscountPercentage()) {
            throw new BadRequestException("Minimum discount must be lower than maximum discount.");
        }

        DiscountRangeConfig range = currentRange();
        range.setMinDiscountPercentage(request.getMinDiscountPercentage());
        range.setMaxDiscountPercentage(request.getMaxDiscountPercentage());

        return discountMapper.toRangeResponse(discountRangeConfigRepository.save(range));
    }

    private DiscountRangeConfig currentRange() {

        return discountRangeConfigRepository.findById(DiscountRangeConfig.SINGLETON_ID)
                .orElseGet(() -> discountRangeConfigRepository.save(DiscountRangeConfig.builder()
                        .id(DiscountRangeConfig.SINGLETON_ID)
                        .minDiscountPercentage(DEFAULT_MIN_DISCOUNT)
                        .maxDiscountPercentage(DEFAULT_MAX_DISCOUNT)
                        .build()));
    }
}
