package com.stylenest.stylenest_backend.service.impl;

import java.util.ArrayList;
import java.math.BigDecimal;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stylenest.stylenest_backend.dto.cart.AddToCartRequest;
import com.stylenest.stylenest_backend.dto.cart.CartResponse;
import com.stylenest.stylenest_backend.dto.cart.UpdateCartRequest;
import com.stylenest.stylenest_backend.entity.Cart;
import com.stylenest.stylenest_backend.entity.CartItem;
import com.stylenest.stylenest_backend.entity.ProductVariant;
import com.stylenest.stylenest_backend.entity.User;
import com.stylenest.stylenest_backend.exception.InsufficientStockException;
import com.stylenest.stylenest_backend.exception.ResourceNotFoundException;
import com.stylenest.stylenest_backend.mapper.CartMapper;
import com.stylenest.stylenest_backend.repository.CartItemRepository;
import com.stylenest.stylenest_backend.repository.CartRepository;
import com.stylenest.stylenest_backend.repository.ProductVariantRepository;
import com.stylenest.stylenest_backend.repository.UserRepository;
import com.stylenest.stylenest_backend.service.CartService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class CartServiceImpl implements CartService {

	private final CartRepository cartRepository;
	private final CartItemRepository cartItemRepository;
	private final ProductVariantRepository productVariantRepository;
	private final UserRepository userRepository;
	private final CartMapper cartMapper;

	private User getCurrentUser() {

		String email = SecurityContextHolder.getContext().getAuthentication().getName();

		return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found."));
	}

	private Cart getOrCreateCart(User user) {

		return cartRepository.findByUser(user).orElseGet(() -> {

			Cart cart = Cart.builder().user(user).items(new ArrayList<>()).build();

			return cartRepository.save(cart);

		});
	}

	@Override
	public CartResponse addToCart(AddToCartRequest request) {

		User user = getCurrentUser();

		Cart cart = getOrCreateCart(user);

		ProductVariant variant = productVariantRepository.findById(request.getProductVariantId())
				.orElseThrow(() -> new ResourceNotFoundException("Product Variant not found."));

		if (request.getQuantity() <= 0) {
			throw new IllegalArgumentException("Quantity must be greater than zero.");
		}

		if (request.getQuantity() > variant.getStock()) {
			throw new InsufficientStockException("Insufficient stock available.");
		}

		CartItem cartItem = cartItemRepository.findByCartAndProductVariant(cart, variant).orElse(null);

		if (cartItem != null) {

			int updatedQuantity = cartItem.getQuantity() + request.getQuantity();

			if (updatedQuantity > variant.getStock()) {
				throw new InsufficientStockException("Insufficient stock available.");
			}

			cartItem.setQuantity(updatedQuantity);

			cartItemRepository.save(cartItem);

		} else {

			CartItem newItem = CartItem.builder().cart(cart).productVariant(variant).quantity(request.getQuantity())
					.price(variant.getProduct().getDiscountPrice() != null ? variant.getProduct().getDiscountPrice()
							: variant.getProduct().getPrice())
					.build();

			cartItemRepository.save(newItem);

			cart.getItems().add(newItem);
		}

		updateCartTotal(cart);

		cartRepository.save(cart);

		return cartMapper.toCartResponse(cart);
	}

	@Override
	public CartResponse getCart() {

		User user = getCurrentUser();

		Cart cart = getOrCreateCart(user);

		updateCartTotal(cart);

		return cartMapper.toCartResponse(cart);
	}

	@Override
	public CartResponse updateQuantity(Long cartItemId, UpdateCartRequest request) {

		User currentUser = getCurrentUser();

		CartItem cartItem = cartItemRepository.findById(cartItemId)
				.orElseThrow(() -> new ResourceNotFoundException("Cart Item not found."));

		if (!cartItem.getCart().getUser().getId().equals(currentUser.getId())) {

			throw new ResourceNotFoundException("Cart Item not found.");
		}

		if (request.getQuantity() <= 0) {
			throw new IllegalArgumentException("Quantity must be greater than zero.");
		}

		ProductVariant variant = cartItem.getProductVariant();

		if (request.getQuantity() > variant.getStock()) {
			throw new InsufficientStockException("Insufficient stock available.");
		}

		cartItem.setQuantity(request.getQuantity());

		cartItemRepository.save(cartItem);

		Cart cart = cartItem.getCart();

		updateCartTotal(cart);

		cartRepository.save(cart);

		return cartMapper.toCartResponse(cart);
	}

	@Override
	public void removeItem(Long cartItemId) {

		User currentUser = getCurrentUser();

		CartItem cartItem = cartItemRepository.findById(cartItemId)
				.orElseThrow(() -> new ResourceNotFoundException("Cart Item not found."));

		if (!cartItem.getCart().getUser().getId().equals(currentUser.getId())) {

			throw new ResourceNotFoundException("Cart Item not found.");
		}

		Cart cart = cartItem.getCart();

		cart.getItems().remove(cartItem);

		cartItemRepository.delete(cartItem);

		updateCartTotal(cart);

		cartRepository.save(cart);
	}

	@Override
	public void clearCart() {

		User user = getCurrentUser();

		Cart cart = getOrCreateCart(user);

		cart.getItems().clear();

		cart.setTotalPrice(BigDecimal.ZERO);

		cartRepository.save(cart);
	}

	private void updateCartTotal(Cart cart) {

		BigDecimal total = cart.getItems().stream()
				.map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		cart.setTotalPrice(total);
	}

}