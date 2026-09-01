package com.stylenest.stylenest_backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import com.stylenest.stylenest_backend.dto.email.OrderConfirmationEmailData;
import com.stylenest.stylenest_backend.dto.invoice.InvoiceItemResponse;
import com.stylenest.stylenest_backend.enums.OrderStatus;
import com.stylenest.stylenest_backend.enums.PaymentMethod;
import com.stylenest.stylenest_backend.enums.PaymentStatus;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

class EmailServiceImplTest {

    private JavaMailSender mailSender;
    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {

        mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));

        emailService = new EmailServiceImpl(mailSender);
        ReflectionTestUtils.setField(emailService, "fromEmail", "test@stylenest.example");
        ReflectionTestUtils.setField(emailService, "fromName", "StyleNest Test");
    }

    @Test
    void sendOrderConfirmationEmail_attachesPdfAndListsLineItemsInInr() throws Exception {

        byte[] pdfBytes = "%PDF-fake-content".getBytes();

        OrderConfirmationEmailData data = OrderConfirmationEmailData.builder()
                .customerName("Jane Doe")
                .orderNumber("SN-1001")
                .orderDate(LocalDateTime.now())
                .items(List.of(InvoiceItemResponse.builder()
                        .productName("Slim Fit Blue Jeans")
                        .color("BLUE")
                        .size("M")
                        .quantity(2)
                        .price(new BigDecimal("1999.00"))
                        .subtotal(new BigDecimal("3998.00"))
                        .build()))
                .subtotal(new BigDecimal("3998.00"))
                .totalAmount(new BigDecimal("3998.00"))
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.PENDING)
                .orderStatus(OrderStatus.CONFIRMED)
                .shippingAddressLine1("123 Main St")
                .shippingCity("Testville")
                .shippingState("TS")
                .shippingPostalCode("123456")
                .shippingCountry("India")
                .build();

        emailService.sendOrderConfirmationEmail("jane@example.com", data, pdfBytes);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();

        assertThat(sent.getSubject()).contains("SN-1001");
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("jane@example.com");

        String html = extractHtmlPart(sent);
        assertThat(html).contains("Slim Fit Blue Jeans");
        assertThat(html).contains("₹3998.00");

        assertThat(hasAttachmentNamed(sent, "Invoice-SN-1001.pdf")).isTrue();
    }

    @Test
    void sendOtpEmail_containsOtpAndNoAttachment() throws Exception {

        emailService.sendOtpEmail("customer@example.com", "123456");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();

        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("customer@example.com");
        assertThat(extractHtmlPart(sent)).contains("123456");
        assertThat(sent.getContent()).isInstanceOf(String.class);
    }

    private String extractHtmlPart(MimeMessage message) throws Exception {

        String found = findHtmlPart(message.getContent());
        if (found == null) {
            throw new AssertionError("No text/html part found in message");
        }
        return found;
    }

    private String findHtmlPart(Object content) throws Exception {

        if (content instanceof String html) {
            return html;
        }

        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart part = multipart.getBodyPart(i);
                if (part.isMimeType("text/html")) {
                    return (String) part.getContent();
                }
                String nested = findHtmlPart(part.getContent());
                if (nested != null) {
                    return nested;
                }
            }
        }

        return null;
    }

    private boolean hasAttachmentNamed(MimeMessage message, String fileName) throws Exception {

        return findAttachment(message.getContent(), fileName);
    }

    private boolean findAttachment(Object content, String fileName) throws Exception {

        if (!(content instanceof Multipart multipart)) {
            return false;
        }

        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            if (fileName.equals(part.getFileName())) {
                return true;
            }
            if (findAttachment(part.getContent(), fileName)) {
                return true;
            }
        }
        return false;
    }
}
