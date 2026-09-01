# StyleNest API contract (condensed — paste into Lovable)

Backend: Spring Boot REST API, base URL `http://localhost:8080` when run locally. Every response is wrapped as `{success, message, data}`. Auth is JWT bearer token (`Authorization: Bearer <token>`).

## Endpoints

```
GET    /
GET    /api/address
POST   /api/address
GET    /api/address/{id}  ?id
PUT    /api/address/{id}  ?id
DELETE /api/address/{id}  ?id
PUT    /api/address/{id}/default  ?id
GET    /api/admin/dashboard
POST   /api/admin/images/upload
GET    /api/admin/latest-orders
GET    /api/admin/low-stock
GET    /api/admin/orders
GET    /api/admin/orders/search  ?keyword, page, size, sortBy, direction
GET    /api/admin/orders/{id}  ?id
GET    /api/admin/orders/{id}/invoice  ?id
GET    /api/admin/orders/{id}/invoice/pdf  ?id
PUT    /api/admin/orders/{id}/shipment  ?id
PUT    /api/admin/orders/{id}/status  ?id
GET    /api/admin/products
POST   /api/admin/products
GET    /api/admin/products/{id}  ?id
PUT    /api/admin/products/{id}  ?id
DELETE /api/admin/products/{id}  ?id
GET    /api/admin/top-selling
POST   /api/auth/forgot-password
POST   /api/auth/login
POST   /api/auth/register
POST   /api/auth/reset-password
POST   /api/auth/verify-otp
GET    /api/cart
POST   /api/cart/add
DELETE /api/cart/clear
PUT    /api/cart/items/{cartItemId}  ?cartItemId
DELETE /api/cart/items/{cartItemId}  ?cartItemId
GET    /api/categories  ?gender
POST   /api/categories
GET    /api/categories/{id}  ?id
PUT    /api/categories/{id}  ?id
DELETE /api/categories/{id}  ?id
GET    /api/checkout
POST   /api/guest/orders
GET    /api/guest/orders/invoice  ?orderNumber, phone
POST   /api/guest/orders/track
DELETE /api/images/{imageId}  ?imageId
GET    /api/orders
POST   /api/orders
GET    /api/orders/{id}  ?id
PUT    /api/orders/{id}/cancel  ?id
GET    /api/orders/{id}/invoice  ?id
GET    /api/orders/{id}/invoice/pdf  ?id
POST   /api/payments/easebuzz/callback  ?responseFields
POST   /api/payments/easebuzz/guest/initiate
POST   /api/payments/easebuzz/initiate
GET    /api/postal-lookup  ?countryCode, postalCode
GET    /api/products
POST   /api/products
GET    /api/products/filter  ?gender, categoryId, color, size, minPrice, maxPrice, search, featured, active, page, sizePerPage, sortBy, direction
POST   /api/products/search
GET    /api/products/{id}  ?id
PUT    /api/products/{id}  ?id
DELETE /api/products/{id}  ?id
GET    /api/products/{productId}/variants  ?productId
POST   /api/products/{productId}/variants  ?productId
GET    /api/users/me
PUT    /api/users/me
PUT    /api/variants/{variantId}  ?variantId
DELETE /api/variants/{variantId}  ?variantId
GET    /api/variants/{variantId}/images  ?variantId
POST   /api/variants/{variantId}/images  ?variantId
GET    /api/wishlist
POST   /api/wishlist/add
DELETE /api/wishlist/clear
DELETE /api/wishlist/items/{wishlistItemId}  ?wishlistItemId
```

## Key data shapes

```
ProductResponse: id:integer, name:string, slug:string, shortDescription:string, description:string, price:number, discountPrice:number, fabric:string, careInstructions:string, featured:boolean, active:boolean, thumbnailUrl:string, availableColors:array, categoryName:string, gender:enum:MEN|WOMEN, createdAt:string, updatedAt:string
ProductRequest: name:string, shortDescription:string, description:string, price:number, discountPrice:number, fabric:string, careInstructions:string, featured:boolean, active:boolean, categoryId:integer
CategoryResponse: id:integer, name:string, slug:string, description:string, imageUrl:string, gender:enum:MEN|WOMEN, active:boolean, createdAt:string, updatedAt:string
CategoryRequest: name:string, description:string, imageUrl:string, gender:enum:MEN|WOMEN, active:boolean
OrderResponse: id:integer, orderNumber:string, totalAmount:number, paymentMethod:enum:COD|CARD|UPI|NETBANKING, paymentStatus:enum:PENDING|PAID|FAILED|REFUNDED|SUCCESS, orderStatus:enum:PENDING|CONFIRMED|PROCESSING|COMPLETED|CANCELLED|PACKED|SHIPPED|DELIVERED, createdAt:string, items:array, isGuest:boolean, shippingAddress:$ref, shipmentStatus:enum:PROCESSING|PACKED|SHIPPED|IN_TRANSIT|OUT_FOR_DELIVERY|DELIVERED|CANCELLED|RETURNED, trackingNumber:string, courierName:string, estimatedDeliveryDate:string, invoiceAvailable:boolean
OrderRequest: paymentMethod:enum:COD|CARD|UPI|NETBANKING
CartResponse: cartId:integer, items:array, totalPrice:number, totalItems:integer
CartItemResponse: cartItemId:integer, productId:integer, productVariantId:integer, productName:string, color:string, size:string, imageUrl:string, price:number, quantity:integer, subTotal:number
AddToCartRequest: productVariantId:integer, quantity:integer
WishlistResponse: wishlistId:integer, items:array, totalItems:integer
RegisterRequest: fullName:string, email:string, password:string, phone:string
LoginRequest: email:string, password:string
AuthResponse: token:string, message:string, fullName:string, email:string, role:string
AddressRequest: fullName:string, phone:string, phoneCountryCode:string, addressLine1:string, addressLine2:string, city:string, state:string, country:string, countryCode:string, postalCode:string, addressType:enum:HOME|OFFICE|OTHER, isDefault:boolean
AddressResponse: id:integer, fullName:string, phone:string, phoneCountryCode:string, addressLine1:string, addressLine2:string, city:string, state:string, country:string, countryCode:string, postalCode:string, addressType:enum:HOME|OFFICE|OTHER, isDefault:boolean, createdAt:string
ProductVariantResponse: id:integer, color:enum:BLACK|WHITE|BLUE|RED|GREEN|BEIGE|GREY|BROWN|PINK|YELLOW|ORANGE|PURPLE, size:enum:XS|S|M|L|XL|XXL, stock:integer, images:array
ProductFilterRequest: keyword:string, categoryId:integer, gender:enum:MEN|WOMEN, color:string, size:string, minPrice:number, maxPrice:number, featured:boolean, active:boolean, page:integer, sizePerPage:integer, sortBy:string, direction:string
CheckoutResponse: items:array, shippingAddress:$ref, totalAmount:number
UserProfileResponse: id:integer, fullName:string, email:string, phone:string, role:string, createdAt:string
```

## Notes for wiring

- `gender` is `MEN` or `WOMEN` — pass it as a query param on category/product list endpoints to filter by segment.
- Pagination endpoints (`/api/products/filter`, `/api/admin/orders/search`) return Spring's standard `Page` shape (`content`, `totalElements`, `totalPages`, `number`, `size`) inside `data`.
- Prices are plain JSON numbers (e.g. `1999.00`).
- Cart/wishlist/addresses/orders/checkout require the bearer token. Guest checkout (`/api/guest/orders`, `/api/guest/orders/track`) does not.
- Admin endpoints (`/api/admin/**` plus admin-only verbs on products/categories/variants) require an ADMIN-role token.

