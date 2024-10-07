
import javax.management.Notification;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author jbachorik
 */
public class CustomNotification extends Notification {
    public CustomNotification(String type, Object source, long sequenceNumber) {
        super(type, source, sequenceNumber);
    }

    public CustomNotification(String type, Object source, long sequenceNumber, String message) {
        super(type, source, sequenceNumber, message);
    }

    public CustomNotification(String type, Object source, long sequenceNumber, long timeStamp) {
        super(type, source, sequenceNumber, timeStamp);
    }

    public CustomNotification(String type, Object source, long sequenceNumber, long timeStamp, String message) {
        super(type, source, sequenceNumber, timeStamp, message);
    }
}
