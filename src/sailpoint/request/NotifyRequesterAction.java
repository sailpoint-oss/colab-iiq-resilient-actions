package sailpoint.request;

import com.acme.resilient.RAction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import sailpoint.api.BasicMessageRepository;
import sailpoint.api.Emailer;
import sailpoint.api.SailPointContext;
import sailpoint.object.*;

import java.util.HashMap;
import java.util.Map;

public class NotifyRequesterAction extends RAction {

    private static final Logger log = LogManager.getLogger(NotifyRequesterAction.class);

    public NotifyRequesterAction(RAction nextAction, SailPointContext context) {
        super(nextAction, context);
    }

    @Override
    protected void doAction(ManagedAttribute managedAttribute, Request request, TaskResult taskResult) throws RequestTemporaryException {
        log.trace("Enter NotifyRequesterAction execute");
        try {
            Identity requester = context.getObjectByName(Identity.class, request.getString("requester"));
            Emailer emailer = new Emailer(context, new BasicMessageRepository());
            EmailTemplate emailTemplate = context.getObjectByName(EmailTemplate.class, "Entitlement deletion requester notification");
            EmailOptions emailOptions = new EmailOptions();
            emailOptions.setTo(requester.getEmail());
            Map<String, Object> templateVariables = new HashMap<>();
            templateVariables.put("identityName", requester.getDisplayName());
            emailOptions.addVariables(templateVariables);
            emailer.sendEmailNotification(emailTemplate, emailOptions);
        } catch (Exception e) {
            throw new RequestTemporaryException("Error notifying requester identity", e);
        }
        log.trace("Exit NotifyRequesterAction execute");
    }
}
