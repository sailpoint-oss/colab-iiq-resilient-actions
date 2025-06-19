package sailpoint.request;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import sailpoint.api.SailPointContext;
import sailpoint.object.*;

import java.text.SimpleDateFormat;
import java.util.Date;

import static sailpoint.object.TaskItemDefinition.Type.Generic;
import static sailpoint.object.TaskResult.CompletionStatus.Success;


public class DeleteEntitlementExecutor extends AbstractRequestExecutor {

    private static final Logger log = LogManager.getLogger(DeleteEntitlementExecutor.class);

    @Override
    public void execute(SailPointContext context, Request request, Attributes<String, Object> attributes) throws RequestPermanentException, RequestTemporaryException {
        log.trace("Starting entitlement deletion");

        NotifyRequesterAction notifyRequesterAction = new NotifyRequesterAction(null, context);
        DeleteEntitlementAction deleteEntitlementAction = new DeleteEntitlementAction(notifyRequesterAction, context);
        DeprovisionExceptionsAction deprovisionExceptionsAction = new DeprovisionExceptionsAction(deleteEntitlementAction, context);
        PropagateRoleChangesAction propagateChangedRoles = new PropagateRoleChangesAction(deprovisionExceptionsAction, context);
        RemoveFromRoleAction removeFromRoleAction = new RemoveFromRoleAction(propagateChangedRoles, context);
        //What is missing here? hint: are we already deleting the entitlement?

        String entitlementId = attributes.getString("entitlementId");
        try {
            ManagedAttribute managedAttribute = context.getObjectById(ManagedAttribute.class, entitlementId);

            TaskResult taskResult;
            String taskResultId = (String) request.get("taskResultId");
            if (taskResultId != null) {
                taskResult = context.getObjectById(TaskResult.class, taskResultId);
            } else {
                taskResult = new TaskResult();
                // generate date string in format day month year hour:minute:second from current date
                String dateString = new SimpleDateFormat("dd MMM yyyy HH:mm:ss").format(new Date());
                taskResult.setName("Delete entitlement \"" + managedAttribute.getDisplayName() + "\" " + dateString);
                taskResult.setLauncher(context.getUserName());
                taskResult.setLaunched(new Date());
                taskResult.setType(Generic);
                TaskDefinition taskDefinition = context.getObjectByName(TaskDefinition.class, "Entitlement Deletion");
                taskResult.setDefinition(taskDefinition);
                context.saveObject(taskResult);
                context.commitTransaction();
                request.put("taskResultId", taskResult.getId());
            }

            removeFromRoleAction.run(managedAttribute, request, taskResult);

            context.decache(taskResult);
            taskResult = context.getObjectById(TaskResult.class, taskResult.getId());
            taskResult.setCompleted(new Date());
            taskResult.setCompletionStatus(Success);
            context.saveObject(taskResult);
            context.commitTransaction();

            log.trace("Finished entitlement deletion");
        } catch (Exception e) {
            throw new RequestTemporaryException(e);
        }
    }

}
