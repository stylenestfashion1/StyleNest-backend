package com.stylenest.stylenest_backend.service.impl;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.stylenest.stylenest_backend.dto.email.OrderConfirmationEmailData;
import com.stylenest.stylenest_backend.dto.invoice.InvoiceItemResponse;
import com.stylenest.stylenest_backend.enums.OrderStatus;
import com.stylenest.stylenest_backend.enums.PaymentMethod;
import com.stylenest.stylenest_backend.enums.PaymentStatus;
import com.stylenest.stylenest_backend.enums.ShipmentStatus;
import com.stylenest.stylenest_backend.exception.EmailSendFailedException;
import com.stylenest.stylenest_backend.service.EmailService;

import jakarta.mail.internet.MimeMessage;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private static final DateTimeFormatter ORDER_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    private final JavaMailSender mailSender;

    public EmailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Value("${app.mail.from}")
    private String fromEmail;

    @Value("${app.mail.from-name}")
    private String fromName;

    @Override
    public void sendOtpEmail(String toEmail, String otp) {

        send(toEmail, "StyleNest Fashion - Password Reset OTP", buildOtpTemplate(otp), null, null,
                "OTP", toEmail);
    }

    @Override
    public void sendOrderConfirmationEmail(String toEmail, OrderConfirmationEmailData data, byte[] invoicePdfBytes) {

        String subject = "StyleNest Fashion - Order #" + data.getOrderNumber() + " Confirmed";
        String attachmentName = invoicePdfBytes == null || invoicePdfBytes.length == 0
                ? null
                : "Invoice-" + data.getOrderNumber() + ".pdf";

        send(toEmail, subject, buildOrderConfirmationTemplate(data),
                attachmentName, invoicePdfBytes,
                "order confirmation", data.getOrderNumber());
    }

    @Override
    public void sendInvoiceEmail(
            String toEmail, String customerName, String invoiceNumber,
            String orderReference, BigDecimal totalAmount, byte[] invoicePdfBytes) {

        String subject = "StyleNest Fashion - Invoice " + invoiceNumber + " for Order #" + orderReference;
        String attachmentName = "Invoice-" + invoiceNumber + ".pdf";

        String body = """
                <h2 style="font-family:Georgia,serif;font-weight:normal;font-size:20px;color:#1a1a1a;margin:0 0 16px 0;">Your Invoice</h2>
                <p style="font-size:14px;line-height:1.6;color:#4a4a4a;margin:0 0 24px 0;">Hi %s, thank you for your order. Your invoice is attached to this email.</p>
                <table style="width:100%%;border-collapse:collapse;font-size:13px;margin-bottom:20px;">
                  <tr><td style="padding:8px 0;color:#9a9488;">Invoice Number</td><td style="padding:8px 0;text-align:right;color:#1a1a1a;">%s</td></tr>
                  <tr><td style="padding:8px 0;color:#9a9488;">Order Reference</td><td style="padding:8px 0;text-align:right;color:#1a1a1a;">#%s</td></tr>
                  <tr><td style="padding:8px 0;color:#9a9488;">Total Amount</td><td style="padding:8px 0;text-align:right;color:#1a1a1a;font-weight:bold;">%s</td></tr>
                </table>
                """.formatted(customerName, invoiceNumber, orderReference, currency(totalAmount));

        send(toEmail, subject, emailShell(body), attachmentName, invoicePdfBytes,
                "invoice", invoiceNumber);
    }

    @Override
    public void sendWelcomeEmail(String toEmail, String customerName) {

        send(toEmail, "Welcome to StyleNest Fashion", buildWelcomeTemplate(customerName), null, null,
                "welcome", toEmail);
    }

    @Override
    public void sendOrderStatusUpdateEmail(
            String toEmail, String customerName, String orderNumber, OrderStatus status) {

        String subject = "StyleNest Fashion - Order #" + orderNumber + " Update";

        send(toEmail, subject, buildOrderStatusUpdateTemplate(customerName, orderNumber, status),
                null, null, "order status update", orderNumber);
    }

    @Override
    public void sendShipmentUpdateEmail(
            String toEmail, String customerName, String orderNumber,
            String courierName, String trackingNumber, ShipmentStatus shipmentStatus) {

        String statusLabel = shipmentStatusLabel(shipmentStatus);
        String subject = "StyleNest Fashion - Order #" + orderNumber + " " + statusLabel;

        send(toEmail, subject,
                buildShipmentUpdateTemplate(customerName, orderNumber, courierName, trackingNumber, shipmentStatus),
                null, null, "shipment update", orderNumber);
    }

    /**
     * Shared send path for every email type. Attaches a PDF only when
     * attachmentBytes is non-null. Logs the technical failure server-side
     * (never the OTP, never credentials) and surfaces only a generic,
     * customer-safe message to the caller -- SMTP/auth details must never
     * reach an API response.
     */
    private void send(
            String toEmail, String subject, String html,
            String attachmentName, byte[] attachmentBytes,
            String emailKind, String context) {

        try {

            MimeMessage message = mailSender.createMimeMessage();
            boolean hasAttachment = attachmentBytes != null && attachmentBytes.length > 0;

            MimeMessageHelper helper = new MimeMessageHelper(message, hasAttachment, "UTF-8");
            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);

            if (hasAttachment) {
                helper.addAttachment(attachmentName, new ByteArrayResource(attachmentBytes));
            }

            mailSender.send(message);

        } catch (Exception ex) {

            log.error("Failed to send {} email (context: {})", emailKind, context, ex);

            throw new EmailSendFailedException(
                    "Unable to send email right now. Please try again later.");
        }
    }

    private String currency(BigDecimal amount) {
        return "₹" + amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String orderStatusLabel(OrderStatus status) {
        return switch (status) {
            case PENDING -> "Pending";
            case CONFIRMED -> "Confirmed";
            case PROCESSING -> "Processing";
            case COMPLETED -> "Completed";
            case CANCELLED -> "Cancelled";
            // Deprecated statuses -- no code path sets these anymore, but a
            // historical order row could still carry one.
            case PACKED -> "Packed";
            case SHIPPED -> "Shipped";
            case DELIVERED -> "Delivered";
        };
    }

    private String shipmentStatusLabel(ShipmentStatus status) {
        if (status == null) {
            return "Shipment Update";
        }
        return switch (status) {
            case PROCESSING -> "Processing";
            case PACKED -> "Packed & Ready for Dispatch";
            case SHIPPED -> "Dispatched";
            case IN_TRANSIT -> "In Transit";
            case OUT_FOR_DELIVERY -> "Out for Delivery";
            case DELIVERED -> "Delivered";
            case CANCELLED -> "Shipment Cancelled";
            case RETURNED -> "Shipment Returned";
        };
    }

    private String emailShell(String bodyHtml) {
        return """
                <!DOCTYPE html>
                <html>
                <body style="margin:0;padding:0;background:#f4f2ef;font-family:Georgia,'Times New Roman',serif;">
                  <div style="max-width:600px;margin:0 auto;padding:40px 20px;">
                    <div style="background:#ffffff;border:1px solid #e5e0d8;">
                      <div style="padding:36px 40px 24px 40px;text-align:center;border-bottom:1px solid #ece7dd;">
                        <div style="font-size:24px;letter-spacing:4px;color:#1a1a1a;font-weight:normal;">STYLENEST</div>
                        <div style="font-size:10px;letter-spacing:2px;color:#a08b6f;margin-top:6px;text-transform:uppercase;font-family:Arial,sans-serif;">Fashion</div>
                      </div>
                      <div style="padding:36px 40px;color:#2a2a2a;font-family:Arial,Helvetica,sans-serif;">
                        %s
                      </div>
                      <div style="padding:24px 40px;background:#faf9f6;border-top:1px solid #ece7dd;text-align:center;">
                        <div style="font-size:11px;color:#9a9488;font-family:Arial,sans-serif;letter-spacing:0.5px;">
                          Thank you for shopping with StyleNest Fashion.
                        </div>
                      </div>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(bodyHtml);
    }

    private String buildOtpTemplate(String otp) {

        String body = """
                <h2 style="font-family:Georgia,serif;font-weight:normal;font-size:20px;color:#1a1a1a;margin:0 0 16px 0;">Password Reset OTP</h2>
                <p style="font-size:14px;line-height:1.6;color:#4a4a4a;margin:0 0 24px 0;">Use the one-time password below to reset your StyleNest Fashion account password.</p>
                <div style="text-align:center;padding:20px 0;">
                  <span style="font-size:32px;letter-spacing:10px;color:#1a1a1a;font-family:Arial,sans-serif;font-weight:bold;">%s</span>
                </div>
                <p style="font-size:13px;color:#7a7a7a;text-align:center;margin:0 0 24px 0;">This OTP expires in 5 minutes.</p>
                <p style="font-size:12px;color:#9a9488;margin:0;">If you didn't request this, you can safely ignore this email -- your password will not be changed.</p>
                """.formatted(otp);

        return emailShell(body);
    }

    private String buildWelcomeTemplate(String customerName) {

        String body = """
                <h2 style="font-family:Georgia,serif;font-weight:normal;font-size:20px;color:#1a1a1a;margin:0 0 16px 0;">Welcome, %s</h2>
                <p style="font-size:14px;line-height:1.6;color:#4a4a4a;margin:0 0 16px 0;">
                  Thank you for creating an account with StyleNest Fashion. Your wardrobe, elevated -- explore
                  our curated Men's and Women's collections whenever you're ready.
                </p>
                <p style="font-size:12px;color:#9a9488;margin:24px 0 0 0;">If you didn't create this account, please ignore this email.</p>
                """.formatted(customerName);

        return emailShell(body);
    }

    private String buildOrderStatusUpdateTemplate(String customerName, String orderNumber, OrderStatus status) {

        String body = """
                <h2 style="font-family:Georgia,serif;font-weight:normal;font-size:20px;color:#1a1a1a;margin:0 0 16px 0;">Order Update</h2>
                <p style="font-size:14px;line-height:1.6;color:#4a4a4a;margin:0 0 20px 0;">Hi %s, your order status has changed.</p>
                <table style="width:100%%;border-collapse:collapse;font-size:13px;margin-bottom:20px;">
                  <tr><td style="padding:8px 0;color:#9a9488;">Order Number</td><td style="padding:8px 0;text-align:right;color:#1a1a1a;">#%s</td></tr>
                  <tr><td style="padding:8px 0;color:#9a9488;">New Status</td><td style="padding:8px 0;text-align:right;color:#1a1a1a;font-weight:bold;">%s</td></tr>
                </table>
                """.formatted(customerName, orderNumber, orderStatusLabel(status));

        return emailShell(body);
    }

    private String buildShipmentUpdateTemplate(
            String customerName, String orderNumber, String courierName,
            String trackingNumber, ShipmentStatus shipmentStatus) {

        String courierRow = (courierName != null && !courierName.isBlank())
                ? """
                  <tr><td style="padding:8px 0;color:#9a9488;">Courier Partner</td><td style="padding:8px 0;text-align:right;color:#1a1a1a;font-weight:bold;">%s</td></tr>
                  """.formatted(courierName)
                : "";

        String trackingRow = (trackingNumber != null && !trackingNumber.isBlank())
                ? """
                  <tr><td style="padding:8px 0;color:#9a9488;">Tracking / AWB Number</td><td style="padding:8px 0;text-align:right;color:#1a1a1a;font-weight:bold;letter-spacing:1px;">%s</td></tr>
                  """.formatted(trackingNumber)
                : "";

        String trackingButton;
        if (trackingNumber != null && !trackingNumber.isBlank()) {
            String trackingUrl = "DTDC".equalsIgnoreCase(courierName)
                    ? "https://track.dtdc.com/ct/tracking-search?trkType=cnno&strcnno=" + trackingNumber
                    : "https://stylenestfashion.com/track-order?orderNumber=" + orderNumber;

            trackingButton = """
                    <div style="text-align:center;margin:30px 0 18px 0;">
                      <a href="%s" target="_blank" style="background:#1a1a1a;color:#ffffff;text-decoration:none;padding:12px 28px;font-size:13px;letter-spacing:1px;text-transform:uppercase;font-family:Arial,sans-serif;display:inline-block;border-radius:2px;font-weight:bold;">Track Your Shipment</a>
                    </div>
                    <p style="font-size:12px;color:#9a9488;text-align:center;margin:0 0 16px 0;">
                      You can also track on <a href="https://www.dtdc.in/" target="_blank" style="color:#a08b6f;text-decoration:underline;">dtdc.in</a> or StyleNest using your AWB number.
                    </p>
                    """.formatted(trackingUrl);
        } else {
            trackingButton = """
                    <div style="text-align:center;margin:30px 0 18px 0;">
                      <a href="https://stylenestfashion.com/track-order?orderNumber=%s" target="_blank" style="background:#1a1a1a;color:#ffffff;text-decoration:none;padding:12px 28px;font-size:13px;letter-spacing:1px;text-transform:uppercase;font-family:Arial,sans-serif;display:inline-block;border-radius:2px;font-weight:bold;">View Order Details</a>
                    </div>
                    """.formatted(orderNumber);
        }

        String body = """
                <h2 style="font-family:Georgia,serif;font-weight:normal;font-size:20px;color:#1a1a1a;margin:0 0 16px 0;">Shipment Update</h2>
                <p style="font-size:14px;line-height:1.6;color:#4a4a4a;margin:0 0 20px 0;">Hi %s, your order shipment status has been updated.</p>
                <table style="width:100%%;border-collapse:collapse;font-size:13px;margin-bottom:12px;">
                  <tr><td style="padding:8px 0;color:#9a9488;">Order Number</td><td style="padding:8px 0;text-align:right;color:#1a1a1a;font-weight:bold;">#%s</td></tr>
                  <tr><td style="padding:8px 0;color:#9a9488;">Current Status</td><td style="padding:8px 0;text-align:right;color:#1a1a1a;font-weight:bold;">%s</td></tr>
                  %s
                  %s
                </table>
                %s
                """.formatted(
                customerName,
                orderNumber,
                shipmentStatusLabel(shipmentStatus),
                courierRow,
                trackingRow,
                trackingButton
        );

        return emailShell(body);
    }

    private String buildOrderConfirmationTemplate(OrderConfirmationEmailData data) {

        String paymentMethodLabel = data.getPaymentMethod() == PaymentMethod.COD
                ? "Cash on Delivery"
                : "Online Payment";

        String paymentStatusLabel = data.getPaymentStatus() == PaymentStatus.PAID
                ? "Paid"
                : data.getPaymentStatus().name();

        String addressLine2 = data.getShippingAddressLine2() == null || data.getShippingAddressLine2().isBlank()
                ? ""
                : ", " + data.getShippingAddressLine2();

        StringBuilder itemRows = new StringBuilder();
        List<InvoiceItemResponse> items = data.getItems() == null ? List.of() : data.getItems();

        for (InvoiceItemResponse item : items) {
            itemRows.append("""
                    <tr>
                      <td style="padding:10px 0;border-bottom:1px solid #ece7dd;font-size:13px;color:#1a1a1a;">
                        %s<br><span style="font-size:11px;color:#9a9488;">%s / %s</span>
                      </td>
                      <td style="padding:10px 0;border-bottom:1px solid #ece7dd;font-size:13px;color:#4a4a4a;text-align:center;">%d</td>
                      <td style="padding:10px 0;border-bottom:1px solid #ece7dd;font-size:13px;color:#4a4a4a;text-align:right;">%s</td>
                      <td style="padding:10px 0;border-bottom:1px solid #ece7dd;font-size:13px;color:#1a1a1a;text-align:right;">%s</td>
                    </tr>
                    """.formatted(
                    item.getProductName(), item.getColor(), item.getSize(),
                    item.getQuantity(), currency(item.getPrice()), currency(item.getSubtotal())));
        }

        String subtotalRow = data.getSubtotal() == null ? "" : """
                <tr><td style="padding:6px 0;color:#9a9488;font-size:13px;">Subtotal</td><td style="padding:6px 0;text-align:right;font-size:13px;color:#1a1a1a;">%s</td></tr>
                """.formatted(currency(data.getSubtotal()));

        String deliveryRow = data.getEstimatedDelivery() == null ? "" : """
                <tr><td style="padding:6px 0;color:#9a9488;font-size:13px;">Estimated Delivery</td><td style="padding:6px 0;text-align:right;font-size:13px;color:#1a1a1a;font-weight:bold;">%s (%s)</td></tr>
                """.formatted(data.getEstimatedDelivery(), data.getCourierName() != null ? data.getCourierName() : "DTDC Ground Economy");

        String trackingRow = data.getTrackingNumber() == null || data.getTrackingNumber().isBlank() ? "" : """
                <tr><td style="padding:6px 0;color:#9a9488;font-size:13px;">Tracking Number</td><td style="padding:6px 0;text-align:right;font-size:13px;color:#1a1a1a;font-weight:bold;">%s (DTDC)</td></tr>
                """.formatted(data.getTrackingNumber());

        String body = """
                <h2 style="font-family:Georgia,serif;font-weight:normal;font-size:20px;color:#1a1a1a;margin:0 0 4px 0;">Order Confirmed</h2>
                <p style="font-size:13px;color:#9a9488;margin:0 0 24px 0;">Order #%s &middot; %s</p>
                <p style="font-size:14px;line-height:1.6;color:#4a4a4a;margin:0 0 24px 0;">Hi %s, your order has been placed successfully.</p>

                <table style="width:100%%;border-collapse:collapse;margin-bottom:16px;">
                  <tr>
                    <td style="padding:8px 0;border-bottom:2px solid #1a1a1a;font-size:10px;letter-spacing:1px;color:#9a9488;text-transform:uppercase;">Item</td>
                    <td style="padding:8px 0;border-bottom:2px solid #1a1a1a;font-size:10px;letter-spacing:1px;color:#9a9488;text-transform:uppercase;text-align:center;">Qty</td>
                    <td style="padding:8px 0;border-bottom:2px solid #1a1a1a;font-size:10px;letter-spacing:1px;color:#9a9488;text-transform:uppercase;text-align:right;">Price</td>
                    <td style="padding:8px 0;border-bottom:2px solid #1a1a1a;font-size:10px;letter-spacing:1px;color:#9a9488;text-transform:uppercase;text-align:right;">Subtotal</td>
                  </tr>
                  %s
                </table>

                <table style="width:100%%;border-collapse:collapse;margin:0 0 28px 0;">
                  %s
                  <tr><td style="padding:6px 0;font-size:14px;color:#1a1a1a;font-weight:bold;">Total</td><td style="padding:6px 0;text-align:right;font-size:14px;color:#1a1a1a;font-weight:bold;">%s</td></tr>
                  <tr><td style="padding:6px 0;color:#9a9488;font-size:13px;">Invoice Number</td><td style="padding:6px 0;text-align:right;font-size:13px;color:#1a1a1a;">%s</td></tr>
                  <tr><td style="padding:6px 0;color:#9a9488;font-size:13px;">Payment Method</td><td style="padding:6px 0;text-align:right;font-size:13px;color:#1a1a1a;">%s</td></tr>
                  <tr><td style="padding:6px 0;color:#9a9488;font-size:13px;">Payment Status</td><td style="padding:6px 0;text-align:right;font-size:13px;color:#1a1a1a;">%s</td></tr>
                  <tr><td style="padding:6px 0;color:#9a9488;font-size:13px;">Order Status</td><td style="padding:6px 0;text-align:right;font-size:13px;color:#1a1a1a;">%s</td></tr>
                  %s
                  %s
                </table>

                <div style="border-top:1px solid #ece7dd;padding-top:20px;">
                  <div style="font-size:10px;letter-spacing:1px;color:#9a9488;text-transform:uppercase;margin-bottom:8px;">Shipping To</div>
                  <p style="font-size:13px;line-height:1.6;color:#4a4a4a;margin:0;">
                    %s%s<br>%s, %s %s<br>%s
                  </p>
                </div>

                <p style="font-size:12px;color:#9a9488;margin-top:24px;">Your invoice is attached to this email.</p>
                """.formatted(
                data.getOrderNumber(),
                data.getOrderDate() == null ? "" : data.getOrderDate().format(ORDER_DATE_FORMAT),
                data.getCustomerName(),
                itemRows.toString(),
                subtotalRow,
                currency(data.getTotalAmount()),
                data.getInvoiceNumber() == null ? "-" : data.getInvoiceNumber(),
                paymentMethodLabel,
                paymentStatusLabel,
                data.getOrderStatus() == null ? "" : orderStatusLabel(data.getOrderStatus()),
                deliveryRow,
                trackingRow,
                data.getShippingAddressLine1(),
                addressLine2,
                data.getShippingCity(),
                data.getShippingState(),
                data.getShippingPostalCode(),
                data.getShippingCountry()
        );

        return emailShell(body);
    }
}
