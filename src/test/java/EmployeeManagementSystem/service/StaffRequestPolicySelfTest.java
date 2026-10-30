package EmployeeManagementSystem.service;
public class StaffRequestPolicySelfTest {
    public static void main(String[] args) {
        String[] roles={"EMPLOYEE","MANAGER","HR","ADMIN","UNKNOWN"};int checks=0;
        for(String reviewer:roles)for(String requester:roles)for(boolean direct:new boolean[]{false,true})for(boolean self:new boolean[]{false,true}) {
            boolean expected=!self && ((requester.equals("HR")||requester.equals("MANAGER"))?reviewer.equals("ADMIN"):requester.equals("EMPLOYEE")&&(reviewer.equals("ADMIN")||reviewer.equals("HR")||reviewer.equals("MANAGER")&&direct));
            if(StaffRequestPolicy.canReview(reviewer,requester,direct,self)!=expected)throw new AssertionError(reviewer+" / "+requester);checks++;
        }
        if(!StaffRequestPolicy.canReview("ROLE_ADMIN","ROLE_HR",false,false))throw new AssertionError("Role normalization");checks++;
        System.out.println("PASS: "+checks+" staff request authorization assertions");
    }
}
