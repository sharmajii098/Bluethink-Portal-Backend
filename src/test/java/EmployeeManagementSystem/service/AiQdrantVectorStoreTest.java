package EmployeeManagementSystem.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class AiQdrantVectorStoreTest {
 @Test void optInOnlyAndFiniteVectors(){
  var disabled=new AiQdrantVectorStore(new MockEnvironment());
  assertFalse(disabled.enabled());assertEquals("mysql",disabled.configuredBackend());
  var enabled=new AiQdrantVectorStore(new MockEnvironment().withProperty("bluethink.ai.vector-store","qdrant"));
  assertTrue(enabled.enabled());assertEquals("qdrant",enabled.configuredBackend());
  assertTrue(AiQdrantVectorStore.validVector(valid()));
  var wrong=new double[511];wrong[0]=1;assertFalse(AiQdrantVectorStore.validVector(wrong));
  var nan=valid();nan[2]=Double.NaN;assertFalse(AiQdrantVectorStore.validVector(nan));
  assertFalse(AiQdrantVectorStore.validVector(new double[512]));
 }
 private double[] valid(){double[] v=new double[512];v[0]=1;return v;}
 @Test void onlyKnownAudienceRolesAreIndexed(){
  assertEquals(java.util.List.of("EMPLOYEE","HR"),AiQdrantVectorStore.roles("|EMPLOYEE|PAYROLL|HR|EMPLOYEE|"));
  assertEquals(java.util.List.of(),AiQdrantVectorStore.roles(null));
 }
 @Test void rejectsUnsafeCollectionNames(){
  var store=new AiQdrantVectorStore(new MockEnvironment().withProperty("bluethink.ai.qdrant.collection","../../admin"));
  assertThrows(IllegalStateException.class,store::collection);
 }
}
