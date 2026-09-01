package com.stylenest.stylenest_backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stylenest.stylenest_backend.dto.scrolldown.ScrollDownImageRequest;
import com.stylenest.stylenest_backend.dto.scrolldown.ScrollDownImageResponse;
import com.stylenest.stylenest_backend.entity.ScrollDownImage;
import com.stylenest.stylenest_backend.enums.Gender;
import com.stylenest.stylenest_backend.exception.BadRequestException;
import com.stylenest.stylenest_backend.mapper.ScrollDownImageMapper;
import com.stylenest.stylenest_backend.repository.ScrollDownImageRepository;

@ExtendWith(MockitoExtension.class)
class ScrollDownImageServiceImplTest {

    @Mock
    private ScrollDownImageRepository scrollDownImageRepository;

    @Mock
    private ScrollDownImageMapper scrollDownImageMapper;

    private ScrollDownImageServiceImpl service;

    private ScrollDownImageServiceImpl service() {
        return new ScrollDownImageServiceImpl(scrollDownImageRepository, scrollDownImageMapper);
    }

    @Test
    void upsert_createsNewRow_whenNoneExistsForGenderAndStep() {

        service = service();

        when(scrollDownImageRepository.findByGenderAndStep(Gender.MEN, 1))
                .thenReturn(Optional.empty());
        when(scrollDownImageRepository.save(any(ScrollDownImage.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(scrollDownImageMapper.toResponse(any(ScrollDownImage.class)))
                .thenReturn(ScrollDownImageResponse.builder().gender(Gender.MEN).step(1).build());

        service.upsert(Gender.MEN, 1, ScrollDownImageRequest.builder().imageUrl("https://x/1.jpg").build());

        ArgumentCaptor<ScrollDownImage> captor = ArgumentCaptor.forClass(ScrollDownImage.class);
        verify(scrollDownImageRepository).save(captor.capture());

        assertThat(captor.getValue().getGender()).isEqualTo(Gender.MEN);
        assertThat(captor.getValue().getStep()).isEqualTo(1);
        assertThat(captor.getValue().getImageUrl()).isEqualTo("https://x/1.jpg");
    }

    @Test
    void upsert_overwritesExistingRow_ratherThanCreatingDuplicate() {

        service = service();

        ScrollDownImage existing = ScrollDownImage.builder()
                .id(9L).gender(Gender.WOMEN).step(2).imageUrl("https://old.jpg").build();

        when(scrollDownImageRepository.findByGenderAndStep(Gender.WOMEN, 2))
                .thenReturn(Optional.of(existing));
        when(scrollDownImageRepository.save(any(ScrollDownImage.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(scrollDownImageMapper.toResponse(any(ScrollDownImage.class)))
                .thenReturn(ScrollDownImageResponse.builder().build());

        service.upsert(Gender.WOMEN, 2, ScrollDownImageRequest.builder().imageUrl("https://new.jpg").build());

        ArgumentCaptor<ScrollDownImage> captor = ArgumentCaptor.forClass(ScrollDownImage.class);
        verify(scrollDownImageRepository, times(1)).save(captor.capture());

        assertThat(captor.getValue().getId()).isEqualTo(9L);
        assertThat(captor.getValue().getImageUrl()).isEqualTo("https://new.jpg");
    }

    @Test
    void upsert_rejectsStepOutsideOneToThree() {

        service = service();

        assertThatThrownBy(() -> service.upsert(
                Gender.MEN, 4, ScrollDownImageRequest.builder().imageUrl("https://x.jpg").build()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void reset_deletesExistingRow() {

        service = service();

        ScrollDownImage existing = ScrollDownImage.builder().id(1L).gender(Gender.MEN).step(1).build();
        when(scrollDownImageRepository.findByGenderAndStep(Gender.MEN, 1))
                .thenReturn(Optional.of(existing));

        service.reset(Gender.MEN, 1);

        verify(scrollDownImageRepository).delete(existing);
    }

    @Test
    void reset_isIdempotent_whenNoRowExists() {

        service = service();

        when(scrollDownImageRepository.findByGenderAndStep(Gender.MEN, 1))
                .thenReturn(Optional.empty());

        service.reset(Gender.MEN, 1);

        verify(scrollDownImageRepository, never()).delete(any());
    }

    @Test
    void getByGender_returnsMappedListSortedByStep() {

        service = service();

        ScrollDownImage row = ScrollDownImage.builder().gender(Gender.WOMEN).step(1).build();
        when(scrollDownImageRepository.findByGenderOrderByStepAsc(Gender.WOMEN))
                .thenReturn(List.of(row));
        when(scrollDownImageMapper.toResponse(row))
                .thenReturn(ScrollDownImageResponse.builder().gender(Gender.WOMEN).step(1).build());

        List<ScrollDownImageResponse> result = service.getByGender(Gender.WOMEN);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStep()).isEqualTo(1);
    }
}
