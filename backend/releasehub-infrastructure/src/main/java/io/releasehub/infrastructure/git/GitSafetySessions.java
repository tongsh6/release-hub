package io.releasehub.infrastructure.git;

import io.releasehub.application.gitoperation.GitOperationJournalPort;
import io.releasehub.application.gitoperation.GitOperationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.UUID;

/** Task 的保留由持久结果事务确认；实际非 Task 组合消费者仍在业务会话结束时确认。 */
@Component @RequiredArgsConstructor
public class GitSafetySessions {
    private static final Object TRANSACTION_KEY = new Object();
    private final ThreadLocal<Session> local = new ThreadLocal<>();
    private final GitOperationJournalPort journal;

    public Scope open() {
        var fence=GitOperationContext.taskFence();
        Session current=local.get();
        if(current!=null) {
            if(current.taskManaged!=(fence!=null) || fence!=null&&!current.id.equals(fence.claimToken())) {
                throw new IllegalStateException("Cannot replace active Git session ownership");
            }
            return new Scope(current,false);
        }
        if(fence!=null) {
            Session session=new Session(fence.claimToken(),false,true);
            local.set(session);
            return new Scope(session,true);
        }
        boolean transactional=TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive();
        Session session=transactional?(Session)TransactionSynchronizationManager.getResource(TRANSACTION_KEY):null;
        if(session==null) {
            session=new Session(UUID.randomUUID().toString(),transactional,false);
            if(transactional) {
                Session captured=session;
                TransactionSynchronizationManager.bindResource(TRANSACTION_KEY,session);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void suspend(){TransactionSynchronizationManager.unbindResourceIfPossible(TRANSACTION_KEY);}
                    @Override public void resume(){TransactionSynchronizationManager.bindResource(TRANSACTION_KEY,captured);}
                    @Override public void afterCompletion(int status) {
                        TransactionSynchronizationManager.unbindResourceIfPossible(TRANSACTION_KEY);
                        if(captured.used)journal.completeSession(captured.id,status==STATUS_COMMITTED&&!captured.failed);
                    }
                });
            }
        }
        local.set(session);return new Scope(session,true);
    }
    private static final class Session {
        final String id;final boolean transactional;final boolean taskManaged;boolean failed;boolean used;
        Session(String id,boolean transactional,boolean taskManaged){this.id=id;this.transactional=transactional;this.taskManaged=taskManaged;}
    }
    public final class Scope implements AutoCloseable {
        private final Session session;private final boolean root;
        private Scope(Session session,boolean root){this.session=session;this.root=root;}
        public String id(){session.used=true;return session.id;}
        public void failed(){session.failed=true;}
        @Override public void close() {
            if(!root)return;
            local.remove();
            if(!session.taskManaged&&!session.transactional&&session.used)journal.completeSession(session.id,!session.failed);
        }
    }
}
