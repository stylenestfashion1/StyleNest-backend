package com.stylenest.stylenest_backend.service.impl;

import java.util.List;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.dto.address.AddressRequest;
import com.stylenest.stylenest_backend.dto.address.AddressResponse;
import com.stylenest.stylenest_backend.entity.Address;
import com.stylenest.stylenest_backend.entity.User;
import com.stylenest.stylenest_backend.exception.AddressHasOrderHistoryException;
import com.stylenest.stylenest_backend.exception.ResourceNotFoundException;
import com.stylenest.stylenest_backend.exception.UnauthorizedAccessException;
import com.stylenest.stylenest_backend.mapper.AddressMapper;
import com.stylenest.stylenest_backend.repository.AddressRepository;
import com.stylenest.stylenest_backend.repository.OrderRepository;
import com.stylenest.stylenest_backend.repository.UserRepository;
import com.stylenest.stylenest_backend.service.AddressService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class AddressServiceImpl implements AddressService {

	private final AddressRepository addressRepository;
	private final UserRepository userRepository;
	private final AddressMapper addressMapper;
	private final OrderRepository orderRepository;

	private User getCurrentUser() {

		String email = SecurityContextHolder.getContext().getAuthentication().getName();

		return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found."));
	}

	@Override
	public AddressResponse addAddress(AddressRequest request) {

		User user = getCurrentUser();

		Address address = addressMapper.toEntity(request);

		address.setUser(user);

		List<Address> addresses = addressRepository.findByUser(user);

		// First Address becomes Default automatically
		if (addresses.isEmpty()) {

			address.setIsDefault(true);

		} else if (Boolean.TRUE.equals(request.getIsDefault())) {

			for (Address a : addresses) {
				a.setIsDefault(false);
			}

			addressRepository.saveAll(addresses);
		}

		Address savedAddress = addressRepository.save(address);

		return addressMapper.toResponse(savedAddress);
	}

	@Override
	@Transactional(readOnly = true)
	public AddressResponse getAddressById(Long id) {

		User user = getCurrentUser();

		Address address = addressRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Address not found with id: " + id));

		validateOwnership(address, user);

		return addressMapper.toResponse(address);
	}

	@Override
	@Transactional(readOnly = true)
	public List<AddressResponse> getAddresses() {

		User user = getCurrentUser();

		return addressRepository.findByUser(user).stream().map(addressMapper::toResponse).toList();
	}

	@Override
	public AddressResponse updateAddress(Long id, AddressRequest request) {

		User user = getCurrentUser();

		Address address = addressRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Address not found with id: " + id));

		validateOwnership(address, user);

		addressMapper.updateEntity(address, request);

		if (Boolean.TRUE.equals(request.getIsDefault())) {

			List<Address> addresses = addressRepository.findByUser(user);

			for (Address a : addresses) {
				a.setIsDefault(false);
			}

			address.setIsDefault(true);

			addressRepository.saveAll(addresses);
		}

		Address updatedAddress = addressRepository.save(address);

		return addressMapper.toResponse(updatedAddress);
	}

	@Override
	public void deleteAddress(Long id) {

		User user = getCurrentUser();

		Address address = addressRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Address not found with id: " + id));

		validateOwnership(address, user);

		// Orders keep a required (non-null) reference to the shipping address
		// used at the time -- it's a historical record, so an address that
		// has ever been used on an order must not be hard-deleted (it would
		// otherwise fail on the orders FK with an unhandled 500).
		if (orderRepository.existsByAddress_Id(id)) {
			throw new AddressHasOrderHistoryException(
					"Cannot delete an address used on an existing order.");
		}

		boolean wasDefault = Boolean.TRUE.equals(address.getIsDefault());

		addressRepository.delete(address);

		if (wasDefault) {

			List<Address> remainingAddresses = addressRepository.findByUser(user);

			if (!remainingAddresses.isEmpty()) {

				Address newDefault = remainingAddresses.get(0);

				newDefault.setIsDefault(true);

				addressRepository.save(newDefault);
			}
		}
	}

	@Override
	public void setDefaultAddress(Long id) {

		User user = getCurrentUser();

		Address selectedAddress = addressRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Address not found with id: " + id));

		validateOwnership(selectedAddress, user);

		List<Address> addresses = addressRepository.findByUser(user);

		for (Address address : addresses) {

			address.setIsDefault(address.getId().equals(selectedAddress.getId()));
		}

		addressRepository.saveAll(addresses);
	}

	private void validateOwnership(Address address, User user) {

		if (!address.getUser().getId().equals(user.getId())) {

			throw new UnauthorizedAccessException("You are not allowed to access this address.");
		}
	}
}