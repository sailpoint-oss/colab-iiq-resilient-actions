package sailpoint.request;

import com.acme.resilient.RAction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import sailpoint.api.Provisioner;
import sailpoint.api.SailPointContext;
import sailpoint.object.*;
import sailpoint.object.ProvisioningPlan.ObjectRequest;
import sailpoint.tools.GeneralException;

import static sailpoint.object.ProvisioningPlan.ObjectOperation.Delete;

public class DeleteEntitlementAction extends RAction {

    private static final Logger log = LogManager.getLogger(DeleteEntitlementAction.class);

    public DeleteEntitlementAction(RAction nextAction, SailPointContext context) {
        super(nextAction, context);
    }

    @Override
    protected boolean isDone(ManagedAttribute managedAttribute) {
        log.trace("Enter DeleteEntitlementAction isDone");
        try {
            int count = context.countObjects(ManagedAttribute.class, new QueryOptions(Filter.eq("value", managedAttribute.getValue())));
            log.trace("Exit DeleteEntitlementAction isDone with count: {}",count == 0);
            return count == 0;
        } catch (GeneralException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    protected void doAction(ManagedAttribute managedAttribute, Request request, TaskResult taskResult) throws RequestTemporaryException {
        try {
            log.trace("Enter DeleteEntitlementAction execute");
            addMessageToTaskResult(taskResult, "Deleting entitlement " + managedAttribute.getValue() + "...");
            context.decache(managedAttribute);
            ProvisioningPlan plan = new ProvisioningPlan();
            ObjectRequest objectRequest = new ObjectRequest();
            objectRequest.setOp(Delete);
            objectRequest.setApplication(managedAttribute.getApplication().getName());
            objectRequest.setNativeIdentity(managedAttribute.getValue());
            objectRequest.setType(managedAttribute.getType());
            plan.add(objectRequest);

            Provisioner provisioner = new Provisioner(context);
            ProvisioningProject project = provisioner.compile(plan);
            provisioner.execute(project);
            if (!isProjectSuccess(project)) {
                throw new RequestTemporaryException("Provisioning plan failed");
            }
            request.addMessage("Entitlement " + managedAttribute.getValue() + " deleted successfully.");
            addMessageToTaskResult(taskResult, "Entitlement " + managedAttribute.getValue() + " deleted successfully.");
            log.trace("Exit DeleteEntitlementAction execute");
        } catch (Exception e) {
            addMessageErrorToTaskResult(taskResult, "Error deleting entitlement " + managedAttribute.getValue() + ": " + e.getMessage());
            throw new RequestTemporaryException(e);
        }

    }
}
