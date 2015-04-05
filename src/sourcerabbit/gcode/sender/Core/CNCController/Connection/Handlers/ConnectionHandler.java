/*
 Copyright (C) 2015  Nikos Siatras

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 SourceRabbit GCode Sender is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package sourcerabbit.gcode.sender.Core.CNCController.Connection.Handlers;

import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;
import sourcerabbit.gcode.sender.Core.CNCController.Connection.Events.SerialConnectionEvents.SerialConnectionEvent;
import sourcerabbit.gcode.sender.Core.CNCController.Connection.Events.SerialConnectionEvents.SerialConnectionEventManager;
import sourcerabbit.gcode.sender.Core.CNCController.Connection.GCode.GRBLGCodeSender;
import sourcerabbit.gcode.sender.Core.CNCController.Tools.ByteArrayBuilder;
import sourcerabbit.gcode.sender.Core.CNCController.Tools.Position3D;

/**
 *
 * @author Nikos Siatras
 */
public class ConnectionHandler implements SerialPortEventListener
{

    // Serial Communication
    protected SerialPort fSerialPort;
    protected String fPortName;
    protected int fBaudRate;
    protected ByteArrayBuilder fIncomingDataBuffer = null;
    protected boolean fConnectionEstablished = false;

    // Incoming Data
    protected String fMessageSplitter;
    protected byte[] fMessageSplitterBytes;
    protected int fMessageSplitterLength, fIndexOfMessageSplitter;
    protected final Object fLockSerialEvent = new Object();

    // CNC position and status
    protected String fActiveState = "";
    protected Position3D fMachinePosition;
    protected Position3D fWorkPosition;

    // Event Managers
    protected SerialConnectionEventManager fSerialConnectionEventManager = new SerialConnectionEventManager();

    // GCode
    protected final GRBLGCodeSender fMyGCodeSender;

    public ConnectionHandler()
    {
        fMyGCodeSender = new GRBLGCodeSender(this);
    }

    /**
     * Try to establish a connection with the CNC Controller
     *
     * @param serialPort
     * @param baudRate
     * @return
     * @throws Exception
     */
    public boolean OpenConnection(String serialPort, int baudRate) throws Exception
    {
        fIncomingDataBuffer = new ByteArrayBuilder();
        fPortName = serialPort;
        fBaudRate = baudRate;

        fSerialPort = new SerialPort(serialPort);
        fSerialPort.openPort();
        fSerialPort.setParams(baudRate, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE, true, true);
        fSerialPort.addEventListener(this);

        if (fSerialPort == null)
        {
            throw new Exception("Serial port not found!");
        }

        fMachinePosition = new Position3D(0, 0, 0);
        fWorkPosition = new Position3D(0, 0, 0);

        return true;
    }

    /**
     * Close the serial connection!
     *
     * @throws Exception
     */
    public void CloseConnection() throws Exception
    {
        if (fSerialPort != null)
        {
            try
            {
                fSerialPort.removeEventListener();

                if (fSerialPort.isOpened())
                {
                    fSerialPort.closePort();
                }
            }
            finally
            {
                fIncomingDataBuffer = null;
                fSerialPort = null;
            }
        }

        fConnectionEstablished = false;

        // Fire connection closed event
        fSerialConnectionEventManager.FireConnectionClosedEvent(new SerialConnectionEvent("Connection Closed"));
    }

    @Override
    public void serialEvent(SerialPortEvent spe)
    {
        synchronized (fLockSerialEvent)
        {
            try
            {
                byte[] incomingBytes = this.fSerialPort.readBytes();
                if (incomingBytes != null && incomingBytes.length > 0)
                {
                    // Data received
                    AppendDataToIncomingBuffer(incomingBytes);
                }
            }
            catch (Exception ex)
            {
            }
        }
    }

    private void AppendDataToIncomingBuffer(byte[] bytes)
    {
        try
        {
            fIncomingDataBuffer.Append(bytes);
            fIndexOfMessageSplitter = fIncomingDataBuffer.IndexOf(fMessageSplitterBytes);
            while (fIndexOfMessageSplitter > -1)
            {
                OnDataReceived(fIncomingDataBuffer.SubList(0, fIndexOfMessageSplitter));
                fIncomingDataBuffer.Delete(0, fIndexOfMessageSplitter + fMessageSplitterLength);
                fIndexOfMessageSplitter = fIncomingDataBuffer.IndexOf(fMessageSplitterBytes);
            }
        }
        catch (Exception ex)
        {
            System.err.println("<Handler_GRBL.AppendDataToIncomingBuffer> Error:" + ex.getMessage());
        }
    }

    public void OnDataReceived(byte[] data)
    {

    }

    public boolean SendData(String data) throws SerialPortException
    {
        try
        {
            return fSerialPort.writeString(data + fMessageSplitter);
        }
        catch (Exception ex)
        {
            try
            {
                CloseConnection();
            }
            catch (Exception ex1)
            {
            }
            throw ex;
        }
    }

    public boolean SendDataImmediately(String data) throws SerialPortException
    {
        return false;
    }

    /**
     * Returns the connection's port name
     *
     * @return
     */
    public String getSerialPortName()
    {
        return fPortName;
    }

    /**
     * Returns the connection's baud rate
     *
     * @return
     */
    public int getBaud()
    {
        return fBaudRate;
    }

    public boolean isConnectionEstablished()
    {
        return fConnectionEstablished;
    }

    public Position3D getMachinePosition()
    {
        return fMachinePosition;
    }

    public String getActiveState()
    {
        return fActiveState;
    }

    public Position3D getWorkPosition()
    {
        return fWorkPosition;
    }

    /**
     * Returns the Serial Connection Event Manager
     *
     * @return ConnectionClosedEventManager
     */
    public SerialConnectionEventManager getSerialConnectionEventManager()
    {
        return fSerialConnectionEventManager;
    }

    /**
     * Returns the handler's GCode sender
     *
     * @return
     */
    public GRBLGCodeSender getMyGCodeSender()
    {
        return fMyGCodeSender;
    }
}
