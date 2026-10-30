package com.example.appointmentservice.service;

import com.example.appointmentservice.dto.ApiResponseError;
import com.example.appointmentservice.dto.AppointmentRequest;
import com.example.appointmentservice.entity.Appointment;
import com.example.appointmentservice.repository.IAppointmentRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

@Service
public class AppointmentService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private IAppointmentRepository appointmentRepository;

    /**
     * Xếp chồng 3 tầng bảo vệ theo đúng yêu cầu đề bài:
     * 1. RateLimiter (Chống spam)
     * 2. CircuitBreaker (Ngắt mạch nếu service chết)
     * 3. Retry (Thử lại nếu lỗi mạng)
     * 4. Fallback (Lưu PENDING vào PostgreSQL và trả ApiResponseError)
     */
    @RateLimiter(name = "doctorRateLimiter", fallbackMethod = "appointmentFallback")
    @CircuitBreaker(name = "doctorServiceCB", fallbackMethod = "appointmentFallback")
    @Retry(name = "doctorRetry", fallbackMethod = "appointmentFallback")
    public Object createAppointment(AppointmentRequest request) {
        System.out.println("[APPOINTMENT SERVICE] Đang gọi sang Doctor-Service...");

        // Giả lập gọi sang Doctor-Service ở cổng 8082
        String doctorUrl = "http://localhost:8082/doctors/check";
        restTemplate.getForObject(doctorUrl, String.class);

        // Nếu gọi thành công -> Lưu DB trạng thái CONFIRMED
        Appointment appointment = Appointment.builder()
                .patientName(request != null ? request.getPatientName() : "N/A")
                .doctorId(request != null ? request.getDoctorId() : 0L)
                .reason(request != null ? request.getReason() : "N/A")
                .status("CONFIRMED")
                .createdAt(LocalDateTime.now())
                .build();

        return appointmentRepository.save(appointment);
    }

    /**
     * Fallback Method: Khi Retry thất bại hết các lần, hoặc CircuitBreaker mở, hoặc bị RateLimit
     */
    public ApiResponseError appointmentFallback(AppointmentRequest request, Throwable throwable) {
        System.err.println("[FALLBACK KÍCH HOẠT] Lý do: " + throwable.getMessage());

        // Đảm bảo dữ liệu đơn hàng vẫn được lưu vào PostgreSQL ở trạng thái "PENDING"
        Appointment pendingAppointment = Appointment.builder()
                .patientName(request != null ? request.getPatientName() : "Khách vãng lai")
                .doctorId(request != null ? request.getDoctorId() : 0L)
                .reason(request != null ? request.getReason() : "N/A")
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();

        appointmentRepository.save(pendingAppointment);
        System.out.println("💾 Đã lưu đơn đặt lịch ID: " + pendingAppointment.getId() + " vào DB với trạng thái PENDING");

        // Trả về đối tượng ApiResponseError JSON chuẩn (status 503)
        return ApiResponseError.builder()
                .message("Không thể kết nối dịch vụ Bác sĩ. Lịch hẹn của bạn đã được ghi nhận ở trạng thái PENDING!")
                .status(503)
                .error("Doctor Service Error: " + throwable.getClass().getSimpleName())
                .timestamp(LocalDateTime.now())
                .build();
    }
}
