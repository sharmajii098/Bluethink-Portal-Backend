package EmployeeManagementSystem.service;
import EmployeeManagementSystem.entity.AiKnowledgeChunk;
import EmployeeManagementSystem.repository.AiKnowledgeRepository;
import org.hibernate.cfg.Configuration;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Real Hibernate/repository queries on isolated H2; does not certify MySQL migrations. */
class KnowledgeRepositoryIntegrationTest {
 @Test void databaseFiltersRoleDepartmentPublicationAndModelBeforeRetrieval(){
  try(var factory=new Configuration().addAnnotatedClass(AiKnowledgeChunk.class).setProperty("hibernate.connection.driver_class","org.h2.Driver").setProperty("hibernate.connection.url","jdbc:h2:mem:knowledge;MODE=MySQL;DB_CLOSE_DELAY=-1").setProperty("hibernate.hbm2ddl.auto","create-drop").setProperty("hibernate.show_sql","false").buildSessionFactory();var em=factory.createEntityManager()){
   em.getTransaction().begin();
   em.persist(row("own","|EMPLOYEE|",1L,true,"model"));em.persist(row("other-department","|EMPLOYEE|",2L,true,"model"));em.persist(row("hr-only","|HR|",null,true,"model"));em.persist(row("withdrawn","|EMPLOYEE|",1L,false,"model"));em.persist(row("old-model","|EMPLOYEE|",1L,true,"old"));em.persist(row("company","|EMPLOYEE|HR|",null,true,"model"));em.getTransaction().commit();em.clear();
   var repo=new JpaRepositoryFactory(em).getRepository(AiKnowledgeRepository.class);
   assertEquals(Set.of("own","company"),new HashSet<>(repo.authorized("%|EMPLOYEE|%",1L,"model",PageRequest.of(0,500)).stream().map(k->k.title).toList()));
   assertEquals(List.of("company"),repo.authorized("%|EMPLOYEE|%",null,"model",PageRequest.of(0,500)).stream().map(k->k.title).toList());
   assertTrue(repo.authorized("%|MANAGER|%",1L,"model",PageRequest.of(0,500)).isEmpty());
   em.getTransaction().begin();var own=em.createQuery("select k from AiKnowledgeChunk k where k.title='own'",AiKnowledgeChunk.class).getSingleResult();assertEquals(1,repo.unpublish(own.sourceId));em.getTransaction().commit();em.clear();
   assertEquals(1,repo.authorized("%|EMPLOYEE|%",1L,"model",PageRequest.of(0,500)).size());
  }
 }
 private AiKnowledgeChunk row(String title,String roles,Long department,boolean published,String model){var k=new AiKnowledgeChunk();k.id=UUID.randomUUID().toString();k.sourceId=UUID.randomUUID().toString();k.title=title;k.filename="policy.txt";k.checksum="a".repeat(64);k.allowedRoles=roles;k.departmentId=department;k.published=published;k.createdBy="ADMIN01";k.createdAt=Instant.now();k.chunkIndex=0;k.content="Allowed facts";k.embedding="[1,0]";k.embeddingModel=model;return k;}
}
