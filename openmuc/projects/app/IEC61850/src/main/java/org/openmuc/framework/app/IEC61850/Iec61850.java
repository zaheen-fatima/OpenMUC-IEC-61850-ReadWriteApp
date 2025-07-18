package org.openmuc.framework.app.IEC61850;

import org.openmuc.framework.data.*;
import org.openmuc.framework.dataaccess.Channel;
import org.openmuc.framework.dataaccess.DataAccessService;
import org.openmuc.framework.dataaccess.RecordListener;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@Component(immediate = true)
public class Iec61850 {

    private static final Logger logger = LoggerFactory.getLogger(Iec61850.class);
    private static final String APP_NAME = "OpenMuc - IEC-61850 APP";

    private DataAccessService dataAccessService;
    private final Map<Channel, RecordListener> channelListenerMap = new HashMap<>();

    @Reference
    public void setDataAccessService(DataAccessService dataAccessService) {
        this.dataAccessService = dataAccessService;
    }

    @Activate
    public void activate() {
        logger.info("{} Activated", APP_NAME);

        String[] channelIds = {"healthStatus", "breakerPosition", "frequency"};

        for (String channelId : channelIds) {
            Channel channel = dataAccessService.getChannel(channelId);
            if (channel != null) {
                logger.info("Channel [{}] initialized successfully", channelId);

                RecordListener listener = new IecListener(channelId);

                channel.addListener(listener);

                channelListenerMap.put(channel, listener);

            } else {
                logger.error("Failed to initialize channel [{}]", channelId);
            }
        }
        writeToChannel("breakerPosition", true);
    }
    public void writeToChannel(String channelId, Object valueToWrite) {
        Channel targetChannel = dataAccessService.getChannel(channelId);

        if (targetChannel != null) {
            try {
                Value value;
                if (valueToWrite instanceof Boolean) {
                    value = new BooleanValue((Boolean) valueToWrite);
                } else if (valueToWrite instanceof Float) {
                    value = new FloatValue((Float) valueToWrite);
                } else {
                    logger.error("Unsupported value type for channel [{}]: {}", channelId, valueToWrite.getClass().getSimpleName());
                    return;
                }

                logger.info("Writing value [{}] to channel [{}]", valueToWrite, channelId);
                targetChannel.write(value);
                logger.info("Successfully wrote value [{}] to channel [{}]", valueToWrite, channelId);
            } catch (Exception e) {
                logger.error("Failed to write value [{}] to channel [{}]: {}", valueToWrite, channelId, e.getMessage(), e);
            }
        } else {
            logger.error("Channel [{}] not found for write operation", channelId);
        }
    }
    private static class IecListener implements RecordListener {
        private final String channelId;

        public IecListener(String channelId) {
            this.channelId = channelId;
        }

        @Override
        public void newRecord(Record record) {
            if (record != null && record.getValue() != null) {
                logger.info("New value from [{}]: {}, Timestamp: {}, Type: {}",
                        channelId,
                        record.getValue(),
                        record.getTimestamp(),
                        record.getValue().getValueType());
            } else {
                logger.warn("Received null record or value from [{}]", channelId);
            }
        }
    }

    @Deactivate
    public void deactivate() {
        logger.info("Deactivating {}", APP_NAME);
        for (Map.Entry<Channel, RecordListener> entry : channelListenerMap.entrySet()) {
            Channel channel = entry.getKey();
            RecordListener listener = entry.getValue();
            channel.removeListener(listener);
            logger.info("Removed listener from channel [{}]", channel.getId());
        }
        channelListenerMap.clear();
    }
}
