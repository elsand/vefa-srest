/* Created by steinar on 01.01.12 at 20:08 */
package no.sr.ringo.queue;

import com.google.inject.Inject;
import no.sr.ringo.account.AccountId;
import no.sr.ringo.account.RingoAccount;
import no.sr.ringo.cenbiimeta.ProfileId;
import no.sr.ringo.guice.jdbc.JdbcTxManager;
import no.sr.ringo.guice.jdbc.Repository;
import no.sr.ringo.message.*;
import no.sr.ringo.message.statistics.InboxStatistics;
import no.sr.ringo.message.statistics.OutboxStatistics;
import no.sr.ringo.message.statistics.RingoAccountStatistics;
import no.sr.ringo.message.statistics.RingoStatistics;
import no.sr.ringo.peppol.PeppolChannelId;
import no.sr.ringo.peppol.PeppolDocumentTypeId;
import no.sr.ringo.peppol.PeppolHeader;
import no.sr.ringo.peppol.PeppolParticipantId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.transform.dom.DOMResult;
import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Repository responsible for handling actions related to outbound message queue
 * @author Adam
 */
@Repository
public class QueueRepositoryImpl implements QueueRepository {

    static final Logger log = LoggerFactory.getLogger(QueueRepositoryImpl.class);

    final JdbcTxManager dataSource;

    @Inject
    public QueueRepositoryImpl(JdbcTxManager jdbcTxManager) {
        this.dataSource = jdbcTxManager;
    }

    @Override
    public OutboundMessageQueueId putMessageOnQueue(Integer msgNo) {
        Connection con = null;
        if (msgNo == null) {
            throw new IllegalStateException("Msg_no required for message to be queued");
        }

        try {
            con = dataSource.getConnection();
            PreparedStatement ps = con.prepareStatement("insert into outbound_message_queue (msg_no, state) " +
                    " values (?,?) ", Statement.RETURN_GENERATED_KEYS);

            ps.setInt(1, msgNo);
            ps.setString(2, OutboundMessageQueueState.QUEUED.name());

            ps.execute();
            ResultSet rs = ps.getGeneratedKeys();

            if (rs.next()) {
                return new OutboundMessageQueueId(rs.getInt(1));
            } else {
                throw new IllegalStateException("Unable to obtain generated key after insert.");
            }

        } catch (SQLException e) {
            throw new IllegalStateException(String.format("Unable to insert put message %d on the queue ", msgNo), e);
        }

    }

    @Override
    public List<QueuedOutboundMessage> getQueuedMessages(long returnLimit) {

        List<QueuedOutboundMessage> result = new ArrayList<QueuedOutboundMessage>();

        String sql = "select q.id, q.msg_no, q.state, m.invoice_no from outbound_message_queue q join message m on (q.msg_no = m.msg_no) where state = ?";
        if (returnLimit > 0) sql = sql + " limit " + returnLimit;

        try {
            Connection con = dataSource.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setString(1, OutboundMessageQueueState.QUEUED.name());
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                OutboundMessageQueueId id = new OutboundMessageQueueId(rs.getInt("id"));
                MessageNumber messageNumber = MessageNumber.create(rs.getInt("msg_no"));
                OutboundMessageQueueState state = OutboundMessageQueueState.valueOf(rs.getString("state"));
                String invoiceNo = rs.getString("invoice_no");

                result.add(new QueuedOutboundMessage(id, messageNumber, state, invoiceNo));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to get queued messages", e);
        }

        return result;
    }

    @Override
    public QueuedOutboundMessage getQueuedMessageById(OutboundMessageQueueId outboundQueueID) {

        String sql = "select q.id, q.msg_no, q.state, m.invoice_no from outbound_message_queue q join message m on (q.msg_no = m.msg_no) where state = ? and id = ?";
        try {
            Connection con = dataSource.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setString(1, OutboundMessageQueueState.QUEUED.name());
            ps.setInt(2, outboundQueueID.toInt());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                OutboundMessageQueueId id = new OutboundMessageQueueId(rs.getInt("id"));
                MessageNumber messageNumber = MessageNumber.create(rs.getInt("msg_no"));
                OutboundMessageQueueState state = OutboundMessageQueueState.valueOf(rs.getString("state"));
                String  invoiceNo = rs.getString("invoice_no");

                return new QueuedOutboundMessage(id, messageNumber, state, invoiceNo);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to get queued messages", e);
        }

        return null;
    }

    @Override
    public void changeQueuedMessageState(OutboundMessageQueueId outboundQueueID, OutboundMessageQueueState state) {
        Connection con = null;

        String sql = "update outbound_message_queue set state = ? where id = ?";

        try {
            con = dataSource.getConnection();

            PreparedStatement ps = con.prepareStatement(sql);
            ps.setString(1, state.name());
            ps.setInt(2, outboundQueueID.toInt());

            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(sql + " failed " + e, e);
        }


    }

    @Override
    public OutboundMessageQueueErrorId logOutboundError(QueuedOutboundMessageError error) {
        Connection con = null;
        String sql = "insert into outbound_message_queue_error (queue_id, details, message, stacktrace) values (?, ?, ?, ?);";

        try {
            con = dataSource.getConnection();

            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, error.getOutboundQueueId().toInt());
            ps.setString(2, error.getDetails());
            ps.setString(3, trimmedString(error.getMessage(), 256)); // message	varchar(256)
            ps.setString(4, error.getStacktrace());

            ps.execute();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                return new OutboundMessageQueueErrorId(rs.getInt(1));
            } else {
                throw new IllegalStateException("Unable to obtain generated key after insert.");
            }
        } catch (SQLException e) {
            throw new IllegalStateException(sql + " failed " + e, e);
        }
    }

    @Override
    public boolean lockQueueItemForDelivery(OutboundMessageQueueId outboundMessageQueueID) {
        Connection con = null;

        String sql = "update outbound_message_queue set state = ? where id = ? and state = ?";

        try {
            con = dataSource.getConnection();

            PreparedStatement ps = con.prepareStatement(sql);
            ps.setString(1, OutboundMessageQueueState.IN_PROGRESS.name());
            ps.setInt(2, outboundMessageQueueID.toInt());
            ps.setString(3, OutboundMessageQueueState.QUEUED.name());

            final int numberOfRowsUpdated = ps.executeUpdate();

            return numberOfRowsUpdated == 1;

        } catch (SQLException e) {
            log.info("Error occurred during acquiring lock", e.getMessage());
            return false;
        }
    }

    //
    // utility functions should be kept private to avoid being intercepted by the Repository annotation
    //

    private String trimmedString(String input, int maxLength) {
        if (input == null) input = "";
        if (input.length() > maxLength) {
            log.debug("Message was trimmed before storing in outbound_message_queue_error to " + maxLength + " characters '" + input + "'");
            input = input.substring(0, maxLength);
        }
        return input;
    }

}
