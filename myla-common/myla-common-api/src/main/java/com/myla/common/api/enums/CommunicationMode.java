package com.myla.common.api.enums;

/**
 * 仪器通信模式枚举。
 * <p>
 * 描述接入网关与实验室仪器之间的连接建立方式。
 * 不同仪器对通信模式的要求不同，驱动层根据此枚举决定如何与仪器建立连接。
 * </p>
 *
 * @author MyLA Team
 */
public enum CommunicationMode {

    /**
     * 被动监听模式。
     * 网关在指定端口监听，等待仪器主动发起连接并推送数据。
     * 典型场景：VITEK 2 等仪器主动向 LIS 发送 ASTM/HL7 报文。
     */
    PASSIVE_LISTEN,

    /**
     * 主动轮询模式。
     * 网关定期检查文件目录或数据库，拉取新产生的数据文件。
     * 典型场景：通过共享文件目录导出结果的仪器。
     */
    ACTIVE_POLL,

    /**
     * 主动连接模式。
     * 网关主动向仪器发起 TCP 连接，建立持久链路后进行双向通信。
     * 典型场景：使用私有协议的仪器，支持命令下发和结果回传。
     */
    ACTIVE_CONNECT
}
