package hudson.plugins.emailext.plugins.recipients;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.*;
import hudson.plugins.emailext.EmailRecipientUtils;
import hudson.plugins.emailext.ExtendedEmailPublisherContext;
import hudson.plugins.emailext.ExtendedEmailPublisherDescriptor;
import hudson.plugins.emailext.plugins.RecipientProvider;
import hudson.plugins.emailext.plugins.RecipientProviderDescriptor;
import hudson.scm.ChangeLogSet;
import hudson.tasks.Mailer;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.mail.internet.InternetAddress;
import java.util.Set;

/**
 * Sends emails to committers of upstream builds which triggered this build.
 */
public class UpstreamComitterRecipientProvider extends RecipientProvider {

    @DataBoundConstructor
    public UpstreamComitterRecipientProvider() {
    }

    @Override
    public void addRecipients(ExtendedEmailPublisherContext context, EnvVars env, Set<InternetAddress> to, Set<InternetAddress> cc, Set<InternetAddress> bcc) {
        ExtendedEmailPublisherDescriptor descriptor = Jenkins.getInstance().getDescriptorByType(ExtendedEmailPublisherDescriptor.class);
        descriptor.debug(context.getListener().getLogger(), "Sending email to upstream committer(s).");

        AbstractBuild<?, ?> cur;
            Cause.UpstreamCause upc = context.getBuild().getCause(Cause.UpstreamCause.class);
            while (upc != null) {
                // UpstreamCause.getUpstreamProject() returns the full name, so use getItemByFullName
                AbstractProject<?, ?> p = (AbstractProject<?, ?>) Jenkins.getInstance().getItemByFullName(upc.getUpstreamProject());
                if(p == null)
                    break;
                cur = p.getBuildByNumber(upc.getUpstreamBuild());
                upc = cur.getCause(Cause.UpstreamCause.class);
                addUpstreamCommittersTriggeringBuild(cur, to, cc, bcc, env, context.getListener());
            }
    }

    /**
     * Adds for the given upstream build the committers to the recipient list for each commit in the upstream build.
     *
     * @param build the upstream build
     * @param to the to recipient list
     * @param cc the cc recipient list
     * @param bcc the bcc recipient list
     * @param env
     * @param listener
     */
    private void addUpstreamCommittersTriggeringBuild(AbstractBuild<?, ?> build, Set<InternetAddress> to, Set<InternetAddress> cc, Set<InternetAddress> bcc, EnvVars env, TaskListener listener) {
        listener.getLogger().println(String.format("Adding upstream committer from job %s with build number %s", build.getProject().getDisplayName(), build.getNumber()));
        for (ChangeLogSet.Entry change : build.getChangeSet()) {
            User user = change.getAuthor();
            String email = user.getProperty(Mailer.UserProperty.class).getAddress();
            if (email != null) {
                listener.getLogger().println(String.format("Adding upstream committer %s to recipient list with email %s", user.getFullName(), email));
                EmailRecipientUtils.addAddressesFromRecipientList(to, cc, bcc, email, env, listener);
            } else {
                listener.getLogger().println(String.format("The user %s does not have a configured email email, trying the user's id", user.getFullName()));
                EmailRecipientUtils.addAddressesFromRecipientList(to, cc, bcc, user.getId(), env, listener);
            }
        }
    }

    @Extension
    public static final class DescriptorImpl extends RecipientProviderDescriptor {

        @Override
        public String getDisplayName() {
            return "Upstream Committer";
        }
    }
}
