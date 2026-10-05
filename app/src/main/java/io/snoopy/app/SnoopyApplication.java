package io.snoopy.app;

import io.snoopy.app.db.entity.TenantEntity;
import io.snoopy.app.db.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = {"io.snoopy"})
@EnableAsync
public class SnoopyApplication {

    private static final Logger log = LoggerFactory.getLogger(SnoopyApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(SnoopyApplication.class, args);
    }

    @Bean
    public CommandLineRunner initDatabase(
            TenantRepository tenantRepository,
            @Value("${snoopy.tenant.default-id:tenant-default}") String defaultTenantId
    ) {
        return args -> {
            if (tenantRepository.findById(defaultTenantId).isEmpty()) {
                TenantEntity defaultTenant = new TenantEntity(defaultTenantId, "Default Enterprise Tenant", "ACTIVE");
                tenantRepository.save(defaultTenant);
                log.info("Initialized default tenant: {}", defaultTenantId);
            }
        };
    }
}
