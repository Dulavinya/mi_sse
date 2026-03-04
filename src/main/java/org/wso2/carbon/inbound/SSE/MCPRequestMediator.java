package org.wso2.carbon.inbound.SSE;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.mediators.AbstractMediator;

public class MCPRequestMediator extends AbstractMediator {

    private static final Log log = LogFactory.getLog(MCPRequestMediator.class);
    private MCPInboundHttpListener httpListener;

    public MCPRequestMediator() {
        log.info("MCPRequestMediator initialized");
    }

    @Override
    public boolean mediate(MessageContext synCtx) {
        log.debug("MCPRequestMediator.mediate() called");

        try {
            // Get the underlying Axis2 MessageContext - try multiple property names
            org.apache.axis2.context.MessageContext axis2MsgCtx = 
                (org.apache.axis2.context.MessageContext) synCtx.getProperty("axis2mctx");
            
            if (axis2MsgCtx == null) {
                // Try alternative property name
                axis2MsgCtx = (org.apache.axis2.context.MessageContext) synCtx.getProperty("AXIS2_CONTEXT");
            }
            
            if (axis2MsgCtx == null) {
                // Last resort: use the property directly from synCtx
                Object msgCtx = synCtx.getProperty(org.apache.axis2.context.MessageContext.class.getName());
                if (msgCtx instanceof org.apache.axis2.context.MessageContext) {
                    axis2MsgCtx = (org.apache.axis2.context.MessageContext) msgCtx;
                }
            }

            if (axis2MsgCtx == null) {
                log.warn("axis2mctx not found in message context, using synCtx directly");
                // If we can't find it, we'll process the synCtx directly
                processRequest(synCtx, null);
                return true;
            }

            // Create listener instance if needed
            if (httpListener == null) {
                httpListener = new MCPInboundHttpListener(
                    synCtx.getEnvironment().getSynapseConfiguration().getProperties(),
                    "MCPEndpoint",
                    synCtx.getEnvironment(),
                    0,
                    null,
                    null,
                    false,
                    false
                );
                httpListener.init();
            }

            // Delegate to onMessage
            httpListener.onMessage(axis2MsgCtx);

            return true;
        } catch (Exception e) {
            log.error("Error in MCPRequestMediator", e);
            return false;
        }
    }

    /**
     * Process request if axis2MsgCtx is not available
     */
    private void processRequest(MessageContext synCtx, org.apache.axis2.context.MessageContext axis2MsgCtx) {
        try {
            log.info("Processing request via Synapse MessageContext");
            // Create listener instance if needed
            if (httpListener == null) {
                httpListener = new MCPInboundHttpListener(
                    synCtx.getEnvironment().getSynapseConfiguration().getProperties(),
                    "MCPEndpoint",
                    synCtx.getEnvironment(),
                    0,
                    null,
                    null,
                    false,
                    false
                );
                httpListener.init();
            }

            if (axis2MsgCtx != null) {
                httpListener.onMessage(axis2MsgCtx);
            } else {
                log.warn("Cannot process - no valid MessageContext available");
            }
        } catch (Exception e) {
            log.error("Error processing request", e);
        }
    }
}
