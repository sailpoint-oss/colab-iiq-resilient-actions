package sailpoint.request;

import com.acme.resilient.RAction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import sailpoint.api.SailPointContext;
import sailpoint.api.Workflower;
import sailpoint.object.*;
import sailpoint.tools.GeneralException;

import java.util.*;

public class RemoveFromRoleAction extends RAction {

    private static final Logger log = LogManager.getLogger(RemoveFromRoleAction.class);

    public RemoveFromRoleAction(RAction nextAction, SailPointContext context) {
        super(nextAction, context);
    }

    @Override
    public boolean isDone(ManagedAttribute managedAttribute) throws RequestTemporaryException {
        try {
            return !isEntitlementInITRole(managedAttribute);
        } catch (GeneralException e) {
            throw new RequestTemporaryException(e);
        }
    }

    @Override
    public void doAction(ManagedAttribute managedAttribute, Request request, TaskResult taskResult) throws RequestTemporaryException {
        try {
            addMessageToTaskResult(taskResult, "Removing from role");
            removeEntitlementFromITRoles(managedAttribute);
            addMessageToTaskResult(taskResult, "Removed from role");
        } catch (Exception e) {
            throw new RequestTemporaryException(e);
        }
    }

    private void removeEntitlementFromITRoles(ManagedAttribute managedAttribute) throws Exception {
        log.trace("Enter removeEntitlementFromITRoles");
        Application application = managedAttribute.getApplication();
        String entitlementValue = managedAttribute.getValue();
        Iterator<Object[]> iter = context.search(Bundle.class, new QueryOptions(Filter.eq("type", "it")), "id");
        List<String> itRoleIds = new ArrayList<>();
        while (iter.hasNext()) {
            Object[] o = iter.next();
            String bundleId = (String) o[0];
            itRoleIds.add(bundleId);
        }
        List<String> affectedItRoles = new ArrayList<>();
        if (!itRoleIds.isEmpty()) {
            for(String itRoleId : itRoleIds) {
                Bundle itRole = context.getObjectById(Bundle.class, itRoleId);
                if (itRole.getProfiles() != null) {
                    itRole.getProfiles()
                            .stream()
                            .filter(profile -> profile.getApplication().getId().equals(application.getId()))
                            .forEach(profile -> {
                                if (profile.getConstraints() != null) {
                                    for (Filter filter : profile.getConstraints()) {
                                        if (filter instanceof Filter.LeafFilter) {
                                            Filter.LeafFilter leafFilter = (Filter.LeafFilter) filter;
                                            List<String> values = (List<String>) leafFilter.getValue();
                                            if (values.contains(entitlementValue)) {
                                                affectedItRoles.add(itRole.getName());
                                                log.trace("Found profile to remove");
                                            }
                                        }
                                    }
                                }
                            });
                }
            }
        }
        affectedItRoles.forEach(itRoleName -> {
            try {
                Bundle itRole = context.getObjectByName(Bundle.class, itRoleName);
                Iterator<Bundle> bundleIterator = context.search(Bundle.class, new QueryOptions(Filter.eq("requirements.name", itRoleName)));
                while (bundleIterator.hasNext()) {
                    Bundle bundle = bundleIterator.next();
                    bundle.removeRequirement(itRole);
                    triggerRoleChangeWfl(bundle, "Remove ent from role", context.getUserName(), false);
                    itRole.getProfiles().clear();
                    context.saveObject(itRole);
                    context.commitTransaction();
                }
            } catch (GeneralException e) {
                throw new RuntimeException(e);
            }

        });


        log.trace("Exit removeEntitlementFromITRoles");
    }

    private boolean isEntitlementInITRole(ManagedAttribute managedAttribute) throws GeneralException {
        log.trace("Enter isEntitlementInITRole");
        Application application = managedAttribute.getApplication();
        String entitlementValue = managedAttribute.getValue();
        Iterator<Object[]> iter = context.search(Bundle.class, new QueryOptions(Filter.eq("type", "it")), "id");
        while (iter.hasNext()) {
            Object[] o = iter.next();
            String bundleId = (String) o[0];
            Bundle itRole = context.getObjectById(Bundle.class, bundleId);
            if (itRole.getProfiles() != null) {
                for (Profile profile : itRole.getProfiles()) {
                    if (profile.getApplication().getId().equals(application.getId())) {
                        if (profile.getConstraints() != null) {
                            for (Filter filter : profile.getConstraints()) {
                                if (filter instanceof Filter.LeafFilter) {
                                    Filter.LeafFilter leafFilter = (Filter.LeafFilter) filter;
                                    List<String> values = (List<String>) leafFilter.getValue();
                                    if (values.contains(entitlementValue)) {
                                        log.trace("Exit isEntitlementInITRole with false");
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        log.trace("Exit isEntitlementInITRole with false");
        return false;
    }

    private void triggerRoleChangeWfl(Bundle changedBundle, String wflCaseName, String launcher, boolean doArchive) throws GeneralException {
        Map<String, Object> wflArgs = new HashMap<>();
        wflArgs.put("approvalObject", changedBundle);
        wflArgs.put("approvalSource", "code");
        wflArgs.put("doRoleAssignment", false);
        wflArgs.put("doArchive", false);
        Workflow workflow = context.getObjectByName(Workflow.class, "Role Modeler - Impact Analysis");
        Workflower workflower = new Workflower(context);
        workflower.launch(workflow, wflCaseName, wflArgs);
    }
}
