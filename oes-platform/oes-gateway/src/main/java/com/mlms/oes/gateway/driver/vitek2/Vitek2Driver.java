package com.mlms.oes.gateway.driver.vitek2;

import com.mlms.oes.common.api.enums.CommunicationMode;
import com.mlms.oes.gateway.channel.TcpChannel;
import com.mlms.oes.gateway.core.model.DiscoveryInfo;
import com.mlms.oes.gateway.core.spi.AbstractInstrumentDriver;
import com.mlms.oes.gateway.splitter.AstmSplitter;

import java.util.List;

/**
 * bioMerieux VITEK 2 仪器驱动。
 * <p>
 * 继承 {@link AbstractInstrumentDriver}，复用可靠性管道（幂等去重 + ACK/NAK + 心跳 + 存档）。
 * 只需声明通信组件和元信息。
 * </p>
 *
 * <h3>数据处理管道：</h3>
 * <pre>
 * TCP(19001) → AstmSplitter(STX/ETX) → Vitek2Parser(O|/R|) → UnifiedResult
 * </pre>
 *
 * @author MLMS Team
 */
public class Vitek2Driver extends AbstractInstrumentDriver {

    public Vitek2Driver() {
        super(new TcpChannel(), new AstmSplitter(), new Vitek2Parser());
    }

    @Override public String getDriverId() { return "vitek2-v1.0"; }
    @Override public String getDisplayName() { return "VITEK 2 Driver"; }
    @Override public String getVersion() { return "1.0"; }
    @Override public CommunicationMode getMode() { return CommunicationMode.PASSIVE_LISTEN; }
    @Override protected String getMessageType() { return "ASTM"; }

    @Override
    public DiscoveryInfo getDiscoveryInfo() {
        DiscoveryInfo info = new DiscoveryInfo();
        info.setManufacturer("bioMerieux");
        info.setModel("VITEK 2");
        info.setSerialNumber("N/A");
        info.setFirmwareVersion("N/A");
        info.setHardwareRevision("N/A");
        info.setSupportedCommands(List.of());
        return info;
    }
}
