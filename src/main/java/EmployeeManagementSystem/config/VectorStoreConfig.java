
package EmployeeManagementSystem.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.mariadb.MariaDBVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class VectorStoreConfig {

    @Bean
    public VectorStore employeeVectorStore(
            @Qualifier("vectorJdbcTemplate") JdbcTemplate jdbcTemplate,
            EmbeddingModel embeddingModel,
            @Value("${spring.ai.vectorstore.mariadb.table-name:vector_store}") String tableName) {

        return MariaDBVectorStore.builder(jdbcTemplate, embeddingModel)
                .vectorTableName(tableName)
                .dimensions(1024)
                .initializeSchema(true)
                .build();
    }
}
