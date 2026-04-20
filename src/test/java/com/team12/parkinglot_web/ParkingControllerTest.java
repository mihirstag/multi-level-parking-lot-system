package com.team12.parkinglot_web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.Collections;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import com.team12.parkinglot_web.payment.PaymentRequest;
import com.team12.parkinglot_web.service.ParkingApplicationService;

@ExtendWith(MockitoExtension.class)
class ParkingControllerTest {

    @Mock
    private ParkingApplicationService parkingApplicationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ParkingController parkingController = new ParkingController(parkingApplicationService);

        InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
        viewResolver.setPrefix("/templates/");
        viewResolver.setSuffix(".html");

        mockMvc = MockMvcBuilders.standaloneSetup(parkingController)
                .setViewResolvers(viewResolver)
                .build();
    }

    @Test
    void checkoutRoute_passesVehicleIdIntoSummary() throws Exception {
        java.util.List<ParkingApplicationService.SpotVehicleSelection> selections = Arrays.asList(
                new ParkingApplicationService.SpotVehicleSelection("F1-C1", "KA01AB1234"),
                new ParkingApplicationService.SpotVehicleSelection("F1-C2", "DL8CAF7788"));
        ParkingApplicationService.CheckoutSummary summary = new ParkingApplicationService.CheckoutSummary(
                selections, "COMPACT", 2, 200.0);

        when(parkingApplicationService.buildCheckoutSummary(
                Arrays.asList("F1-C1", "F1-C2"),
                Arrays.asList("KA01AB1234", "DL8CAF7788"),
                "COMPACT",
                2)).thenReturn(summary);

        mockMvc.perform(post("/checkout")
                        .sessionAttr("userEmail", "driver@example.com")
                        .sessionAttr("userRole", ParkingApplicationService.ROLE_DRIVER)
                        .param("spotIds", "F1-C1", "F1-C2")
                        .param("vehicleIds", "KA01AB1234", "DL8CAF7788")
                        .param("vehicleType", "COMPACT")
                        .param("hours", "2")
                        )
                .andExpect(status().isOk())
                .andExpect(view().name("checkout"))
                .andExpect(model().attribute("vehicleType", "COMPACT"));

        verify(parkingApplicationService).buildCheckoutSummary(
                Arrays.asList("F1-C1", "F1-C2"),
                Arrays.asList("KA01AB1234", "DL8CAF7788"),
                "COMPACT",
                2);
    }

    @Test
    void payRoute_redirectsToSuccessAndForwardsVehicleId() throws Exception {
        when(parkingApplicationService.finalizePayment(
                eq(Collections.singletonList("F1-C1")),
                eq(Collections.singletonList("KA01AB1234")),
                eq("COMPACT"),
                eq(1),
                any(PaymentRequest.class),
                eq("driver@example.com"))).thenReturn(true);

        mockMvc.perform(post("/pay")
                        .sessionAttr("userEmail", "driver@example.com")
                        .sessionAttr("userRole", ParkingApplicationService.ROLE_DRIVER)
                        .param("spotIds", "F1-C1")
                        .param("vehicleIds", "KA01AB1234")
                        .param("vehicleType", "COMPACT")
                        .param("hours", "1")
                        .param("paymentMethod", "CASH"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/success"));

        ArgumentCaptor<PaymentRequest> requestCaptor = ArgumentCaptor.forClass(PaymentRequest.class);
        verify(parkingApplicationService).finalizePayment(
                eq(Collections.singletonList("F1-C1")),
                eq(Collections.singletonList("KA01AB1234")),
                eq("COMPACT"),
                eq(1),
                requestCaptor.capture(),
                eq("driver@example.com"));
        PaymentRequest capturedRequest = requestCaptor.getValue();

        assertEquals("CASH", capturedRequest.getNormalizedPaymentMethod());
    }

    @Test
    void adminIncidents_populatesActiveSessions() throws Exception {
        Map<String, String> activeSession = new HashMap<String, String>();
        activeSession.put("ticketId", "TKT-1001");
        activeSession.put("vehicleId", "KA01AB1234");

        when(parkingApplicationService.getAllViolations()).thenReturn(Collections.<Map<String, String>>emptyList());
        when(parkingApplicationService.getRecentGateOverrides(50)).thenReturn(Collections.<Map<String, String>>emptyList());
        when(parkingApplicationService.getRecentLostTicketCases(50)).thenReturn(Collections.<Map<String, String>>emptyList());
        when(parkingApplicationService.getActiveParkingSessions(100)).thenReturn(Collections.singletonList(activeSession));

        mockMvc.perform(get("/admin/incidents")
                        .sessionAttr("userEmail", "admin@parkmanager.com")
                        .sessionAttr("userRole", ParkingApplicationService.ROLE_ADMIN))
                .andExpect(status().isOk())
                .andExpect(view().name("admin_incidents"))
                .andExpect(model().attributeExists("activeSessions"));

        verify(parkingApplicationService).getActiveParkingSessions(100);
    }

    @Test
    void adminIncidents_redirectsNonAdminToOwnDashboard() throws Exception {
        when(parkingApplicationService.getDashboardPathForRole(ParkingApplicationService.ROLE_DRIVER))
                .thenReturn("/dashboard/driver");

        mockMvc.perform(get("/admin/incidents")
                        .sessionAttr("userEmail", "driver@example.com")
                        .sessionAttr("userRole", ParkingApplicationService.ROLE_DRIVER))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard/driver"));
    }

    @Test
    void adminSessionLookup_displaysLookupResult() throws Exception {
        when(parkingApplicationService.getAllViolations()).thenReturn(Collections.<Map<String, String>>emptyList());
        when(parkingApplicationService.getRecentGateOverrides(50)).thenReturn(Collections.<Map<String, String>>emptyList());
        when(parkingApplicationService.getRecentLostTicketCases(50)).thenReturn(Collections.<Map<String, String>>emptyList());
        when(parkingApplicationService.getActiveParkingSessions(100)).thenReturn(Collections.<Map<String, String>>emptyList());

        Map<String, String> lookupResult = new HashMap<String, String>();
        lookupResult.put("vehicleId", "KA01AB1234");
        lookupResult.put("ticketId", "TKT-2002");

        when(parkingApplicationService.findActiveSessionByVehicle("KA01AB1234")).thenReturn(lookupResult);

        mockMvc.perform(post("/admin/incidents/session-search")
                        .sessionAttr("userEmail", "admin@parkmanager.com")
                        .sessionAttr("userRole", ParkingApplicationService.ROLE_ADMIN)
                        .param("vehicleId", "KA01AB1234"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin_incidents"))
                .andExpect(model().attributeExists("activeSessionLookupResult"));

        verify(parkingApplicationService).findActiveSessionByVehicle("KA01AB1234");
    }
}
