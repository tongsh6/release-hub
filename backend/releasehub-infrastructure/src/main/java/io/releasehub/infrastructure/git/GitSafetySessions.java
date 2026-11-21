package io.releasehub.infrastructure.git;

import io.releasehub.application.gitoperation.GitOperationJournalPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.UUID;

/** 同步组合操作共享保留；外层业务事务结束后才决定是否可释放。 */
@Component @RequiredArgsConstructor
public class GitSafetySessions {
    private static final Object TRANSACTION_KEY = new Object();
    private final ThreadLocal<Session> local = new ThreadLocal<>();
    private final GitOperationJournalPort journal;

    public Scope open() {
        Session current=local.get();
        if(current!=null) return new Scope(current,false);
        boolean transactional=TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive();
        Session session=transactional?(Session)TransactionSynchronizationManager.getResource(TRANSACTION_KEY):null;
        if(session==null) {
            session=new Session(UUID.randomUUID().toString(),transactional);
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
        final String id;final boolean transactional;boolean failed;boolean used;
        Session(String id,boolean transactional){this.id=id;this.transactional=transactional;}
    }
    public final class Scope implements AutoCloseable {
        private final Session session;private final boolean root;
        private Scope(Session session,boolean root){this.session=session;this.root=root;}
        public String id(){session.used=true;return session.id;}
        public void failed(){session.failed=true;}
        @Override public void close() {
            if(!root)return;
            local.remove();
            if(!session.transactional&&session.used)journal.completeSession(session.id,!session.failed);
        }
    }
}
