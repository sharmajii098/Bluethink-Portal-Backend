package EmployeeManagementSystem.config;
import EmployeeManagementSystem.entity.OfficeLocation;
import EmployeeManagementSystem.repository.OfficeLocationRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Explicit provisioning; never overwrites an existing office. */
@Component @Profile("bootstrap") @Order(-10)
public class BootstrapOfficeRunner implements ApplicationRunner {
    private final OfficeLocationRepository offices;
    private final Environment env;
    public BootstrapOfficeRunner(OfficeLocationRepository offices,Environment env){this.offices=offices;this.env=env;}
    @Override public void run(ApplicationArguments args){
        if(offices.findByOfficeName("Main Office").isPresent())return;
        try {
            double lat=Double.parseDouble(env.getRequiredProperty("BLUETHINK_OFFICE_LATITUDE"));
            double lon=Double.parseDouble(env.getRequiredProperty("BLUETHINK_OFFICE_LONGITUDE"));
            double radius=Double.parseDouble(env.getProperty("BLUETHINK_OFFICE_RADIUS_METERS","200"));
            if(!Double.isFinite(lat)||!Double.isFinite(lon)||!Double.isFinite(radius)||Math.abs(lat)>90||Math.abs(lon)>180||radius<=0||radius>10000)throw new IllegalArgumentException();
            offices.save(OfficeLocation.builder().officeName("Main Office").latitude(lat).longitude(lon).allowedRadius(radius).build());
        }catch(IllegalArgumentException error){throw new IllegalStateException("Set valid BLUETHINK_OFFICE_LATITUDE, BLUETHINK_OFFICE_LONGITUDE and BLUETHINK_OFFICE_RADIUS_METERS (1–10000) before initial provisioning.");}
    }
}
