package com.acme.resilient;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.*;
import sailpoint.request.RequestTemporaryException;
import sailpoint.tools.Message;

import static sailpoint.object.ProvisioningResult.STATUS_COMMITTED;

public abstract class RAction {
    private final RAction nextAction;
    protected SailPointContext context;

    private static final Logger log = LogManager.getLogger(RAction.class);

    public RAction(RAction nextAction, SailPointContext context) {
        this.nextAction = nextAction;
        this.context = context;
    }

    protected boolean isDone(ManagedAttribute managedAttribute) throws RequestTemporaryException {
        return false;
    }

    protected boolean isRunning(ManagedAttribute managedAttribute) throws RequestTemporaryException {
        return false;
    }

    protected abstract void doAction(ManagedAttribute managedAttribute, Request request, TaskResult taskResult) throws RequestTemporaryException;

    public void run(ManagedAttribute managedAttribute, Request request, TaskResult taskResult) throws RequestTemporaryException {
        if (isDone(managedAttribute)) {
            if (nextAction != null) {
                nextAction.run(managedAttribute, request, taskResult);
            }
        } else {
            if (isRunning(managedAttribute)) {
                throw new RequestTemporaryException("Process already running");
            } else {
                doAction(managedAttribute, request, taskResult);
                if (nextAction != null) {
                    nextAction.run(managedAttribute, request, taskResult);
                }
            }
        }
    }











    public boolean isProjectSuccess(ProvisioningProject project) {
        boolean isSuccess = true;
        for (ProvisioningPlan plan: project.getPlans()) {
            ProvisioningResult planResult = plan.getResult();
            if (planResult != null && !STATUS_COMMITTED.equals(planResult.getStatus())) {
                isSuccess = false;
                break;
            }
            if (isSuccess) {
                if (plan.getAccountRequests() != null) {
                    for (ProvisioningPlan.AccountRequest accountRequest : plan.getAccountRequests()) {
                        if (accountRequest.getResult() != null && !STATUS_COMMITTED.equals(accountRequest.getResult().getStatus())) {
                            isSuccess = false;
                            break;
                        }
                    }
                }
                if (plan.getObjectRequests() != null) {
                    for (ProvisioningPlan.ObjectRequest objectRequest : plan.getObjectRequests()) {
                        if (objectRequest.getResult() != null && !STATUS_COMMITTED.equals(objectRequest.getResult().getStatus())) {
                            isSuccess = false;
                            break;
                        }
                    }
                }
            }
        }
        return isSuccess;
    }

    public void addMessageToTaskResult(TaskResult taskResult, String message) {
        addMessageToTaskResult(taskResult, new Message(Message.Type.Info, message));
    }

    public void addMessageErrorToTaskResult(TaskResult taskResult, String message) {
        addMessageToTaskResult(taskResult, new Message(Message.Type.Error, message));
    }

    public void addMessageToTaskResult(TaskResult taskResult, Message message) {
        try {
            SailPointContext privateContext = SailPointFactory.createPrivateContext();
            TaskResult privateTaskResult = privateContext.getObjectById(TaskResult.class, taskResult.getId());
            privateTaskResult.addMessage(message);
            privateContext.saveObject(privateTaskResult);
            privateContext.commitTransaction();
            privateContext.decache(privateTaskResult);
            privateContext.close();
        } catch (Exception e) {
            log.error("Error adding message to task result", e);
        }
    }
}
