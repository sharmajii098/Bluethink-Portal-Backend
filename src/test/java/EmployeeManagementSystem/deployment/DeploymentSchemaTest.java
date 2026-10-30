package EmployeeManagementSystem.deployment;

import jakarta.persistence.Entity;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Generates the initial MySQL schema from the same entity mappings used in production. No database writes. */
class DeploymentSchemaTest {
    @Test void generateMySqlBaselineFromAllEntities() throws Exception {
        Path output = Path.of("target/deployment/001_initial_schema.sql");
        Files.createDirectories(output.getParent()); Files.writeString(output, "");
        Map<String,Object> settings = new HashMap<>();
        settings.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        settings.put("hibernate.boot.allow_jdbc_metadata_access", false);
        settings.put("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl");
        settings.put("hibernate.implicit_naming_strategy", "org.springframework.boot.hibernate.SpringImplicitNamingStrategy");
        settings.put("jakarta.persistence.schema-generation.database.action", "none");
        settings.put("jakarta.persistence.schema-generation.scripts.action", "create");
        settings.put("jakarta.persistence.schema-generation.scripts.create-target", output.toAbsolutePath().toString());
        settings.put("hibernate.hbm2ddl.delimiter", ";");
        var registry = new StandardServiceRegistryBuilder().applySettings(settings).build();
        try {
            var sources = new MetadataSources(registry);
            try (var paths = Files.list(Path.of("src/main/java/EmployeeManagementSystem/entity"))) {
                for (Path path : paths.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                    String name = path.getFileName().toString().replace(".java", "");
                    Class<?> type = Class.forName("EmployeeManagementSystem.entity." + name);
                    if (type.isAnnotationPresent(Entity.class)) sources.addAnnotatedClass(type);
                }
            }
            var metadata = sources.buildMetadata();
            SchemaManagementToolCoordinator.process(metadata, registry, settings, action -> {});
            String sql = Files.readString(output);
            assertTrue(sql.contains("create table employees"));
            assertTrue(sql.contains("create table login_location"));
            assertTrue(sql.contains("idx_login_location_day_employee"));
            assertFalse(sql.toLowerCase(Locale.ROOT).contains("drop table"));
            var tables = sql.lines().filter(line -> line.startsWith("create table ")).toList();
            assertEquals(tables.size(), new HashSet<>(tables).size(), "Baseline must not contain duplicate table declarations");
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }
}
