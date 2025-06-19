package sailpoint.request;

import com.acme.resilient.RAction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import sailpoint.api.Provisioner;
import sailpoint.api.SailPointContext;
import sailpoint.object.*;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.tools.GeneralException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static sailpoint.object.ProvisioningPlan.AccountRequest.Operation.Modify;

public class DeprovisionExceptionsAction extends RAction {

    private static final Logger log = LogManager.getLogger(DeprovisionExceptionsAction.class);

    public DeprovisionExceptionsAction(RAction nextAction, SailPointContext context) {
        super(nextAction, context);
    }

    @Override
    protected boolean isDone(ManagedAttribute managedAttribute) throws RequestTemporaryException {
        log.trace("Enter DeprovisionExceptionsAction isDone");
        boolean isDone;
        try {
            int count = context.countObjects(IdentityEntitlement.class, new QueryOptions(Filter.eq("value", managedAttribute.getValue())));
            isDone = count == 0;
        } catch (GeneralException e) {
            throw new RequestTemporaryException(e);
        }
        log.trace("Exit DeprovisionExceptionsAction isDone with result: {}", isDone);
        return isDone;
    }

    @Override
    protected void doAction(ManagedAttribute managedAttribute, Request request, TaskResult taskResult) throws RequestTemporaryException {
        log.trace("Enter DeprovisionExceptionsAction execute");
        addMessageToTaskResult(taskResult, "Deprovisioning exceptions");
        try {
            Iterator<Object[]> entitlementOIterator = context.search(IdentityEntitlement.class, new QueryOptions(Filter.eq("value", managedAttribute.getValue())), "id");
            List<String> identityEntitlementIds = new ArrayList<>();
            while (entitlementOIterator.hasNext()) {
                identityEntitlementIds.add(entitlementOIterator.next()[0].toString());
            }
            for (String identityEntitlementId: identityEntitlementIds) {
                IdentityEntitlement identityEntitlement = context.getObjectById(IdentityEntitlement.class, identityEntitlementId);
                Provisioner provisioner = new Provisioner(context);
                ProvisioningPlan plan = createRemovePlan(identityEntitlement);
                ProvisioningProject project = provisioner.compile(plan);
                provisioner.execute(project);
                if (!isProjectSuccess(project)) {
                    throw new RequestTemporaryException("Error provisioning removal of entitlement from identity.");
                }
            }
            request.addMessage("Entitlement " + managedAttribute.getValue() + " deprovisioned from all identities successfully.");
            addMessageToTaskResult(taskResult, "Entitlement " + managedAttribute.getValue() + " deprovisioned from all identities successfully.");
        } catch (Exception e) {
            addMessageErrorToTaskResult(taskResult, "Error deprovisioning removal of entitlement from identity: " + e.getMessage());
            throw new RequestTemporaryException("Error provisioning removal of entitlement from identity.", e);
        }
        log.trace("Exit DeprovisionExceptionsAction execute");
    }

    private ProvisioningPlan createRemovePlan(IdentityEntitlement identityEntitlement) {
        ProvisioningPlan plan = new ProvisioningPlan();
        plan.setIdentity(identityEntitlement.getIdentity());

        AccountRequest accountRequest = new AccountRequest(Modify, identityEntitlement.getAppName(), null, identityEntitlement.getNativeIdentity());
        accountRequest.add(new AttributeRequest(identityEntitlement.getName() , sailpoint.object.ProvisioningPlan.Operation.Remove, identityEntitlement.getValue()));
        plan.add(accountRequest);

        return plan;
    }
}
