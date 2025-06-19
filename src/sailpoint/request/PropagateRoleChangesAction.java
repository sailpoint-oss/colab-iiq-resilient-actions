package sailpoint.request;

import com.acme.resilient.RAction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import sailpoint.api.SailPointContext;
import sailpoint.api.TaskManager;
import sailpoint.object.*;
import sailpoint.reporting.JasperExecutor;
import sailpoint.tools.GeneralException;

import java.util.Calendar;
import java.util.Iterator;
import java.util.Locale;

public class PropagateRoleChangesAction extends RAction {

    private static final Logger log = LogManager.getLogger(PropagateRoleChangesAction.class);

    public PropagateRoleChangesAction(RAction nextAction, SailPointContext context) {
        super(nextAction, context);
    }

    @Override
    public boolean isRunning(ManagedAttribute managedAttribute) throws RequestTemporaryException {
        log.trace("Enter propagateChangedRoles isRunning");
        try {
            QueryOptions queryOptions = new QueryOptions(Filter.eq("definition.name", "Propagate Role Changes"));
            queryOptions.setOrderBy("created");
            queryOptions.setOrderAscending(false);
            Iterator<TaskResult> taskResultIterator = context.search(TaskResult.class, queryOptions);
            if (taskResultIterator.hasNext()) {
                TaskResult taskResult = taskResultIterator.next();
                boolean result = taskResult.getCompleted() == null;
                log.trace("Exit propagateChangedRoles isRunning with result {}", result);
                return result;
            } else {
                log.trace("Exit propagateChangedRoles isRunning with false");
                return false;
            }
        } catch (GeneralException e) {
            throw new RequestTemporaryException(e);
        }
    }

    @Override
    public void doAction(ManagedAttribute managedAttribute, Request request, TaskResult taskResult) throws RequestTemporaryException {
        log.trace("Enter propagateChangedRoles execute");
        addMessageToTaskResult(taskResult, "Propagating role changes");
        try {
            TaskManager taskManager = new TaskManager(context);
            taskManager.setLauncher(context.getUserName());
            TaskDefinition taskDefinition = context.getObjectByName(TaskDefinition.class, "Propagate Role Changes task");
            Attributes<String,Object> inputs = new Attributes<>();
            inputs.put(JasperExecutor.OP_LOCALE, Locale.getDefault().toString());
            inputs.put(JasperExecutor.OP_TIME_ZONE, Calendar.getInstance().getTimeZone().getID());
            taskManager.runSync(taskDefinition, inputs);
            addMessageToTaskResult(taskResult, "Propagating role changes done");
        } catch (Throwable e) {
            addMessageErrorToTaskResult(taskResult, "Error propagating role changes: " + e.getMessage());
            throw new RequestTemporaryException(e);
        }
        log.trace("Exit propagateChangedRoles execute");
    }
}
