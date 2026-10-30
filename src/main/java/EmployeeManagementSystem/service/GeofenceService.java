package EmployeeManagementSystem.service;

import EmployeeManagementSystem.entity.OfficeLocation;
import EmployeeManagementSystem.repository.OfficeLocationRepository;
import EmployeeManagementSystem.util.DistanceCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Uses only an explicitly configured office. Never logs employee coordinates. */
@Service @RequiredArgsConstructor
public class GeofenceService {
    private final OfficeLocationRepository repository;
    public OfficeLocation getOffice() {
        OfficeLocation office=repository.findByOfficeName("Main Office")
            .orElseThrow(()->new IllegalStateException("Office location is not configured. Contact your administrator."));
        validate(office.getLatitude(),office.getLongitude());
        Double radius=office.getAllowedRadius();
        if(radius==null || !Double.isFinite(radius) || radius<=0 || radius>10000)
            throw new IllegalStateException("Office radius must be configured between 1 and 10000 metres.");
        return office;
    }
    private void validate(Double lat, Double lon) {
        if(lat==null || lon==null || !Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat)>90 || Math.abs(lon)>180)
            throw new IllegalArgumentException("Valid latitude and longitude are required.");
    }
    public double getDistance(Double latitude, Double longitude) {
        validate(latitude,longitude);
        OfficeLocation office=getOffice();
        return DistanceCalculator.calculateDistance(latitude,longitude,office.getLatitude(),office.getLongitude());
    }
    public boolean isInsideOffice(Double latitude, Double longitude) {
        return getDistance(latitude,longitude)<=getOffice().getAllowedRadius();
    }
}
