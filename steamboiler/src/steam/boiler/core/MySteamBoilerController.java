package steam.boiler.core;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import steam.boiler.model.SteamBoilerController;
import steam.boiler.util.Mailbox;
import steam.boiler.util.Mailbox.Message;
import steam.boiler.util.Mailbox.MessageKind;
import steam.boiler.util.MemoryAnnotations.Initialisation;
import steam.boiler.util.SteamBoilerCharacteristics;


/**
 * Controller for the steam boiler. 
 * @author Caitlin
 *
 */
public class MySteamBoilerController implements SteamBoilerController {
  
  /**
  * Captures the various modes in which the controller can operate.
  *
  * @author David J. Pearce
  */
  
  /**
   * The length of the cycle which is five seconds. 
   */
  private int cycle = 5;
  
  /**
   * Number of pumps in the steam boiler. 
   */
  private int numberOfPumps;
  
  /**
   * The capacity of the pumps in the steam boiler.
   */
  private double pumpCapacity;
  
  /**
   * The current water level in the boiler. 
   */
  private double waterLevel;
  
  /**
   * THe maximum water capacity of the boiler.
   */
  private double waterCapacity;
  
  /**
   * The current steam level in the boiler. 
   */
  private double steamLevel;
  
  /**
   * The maximum steam level that there can be.
   */
  private double maxSteamLevel;
  
  /**
   * The maximum water level to still be in the normal section. 
   */
  private double maxNormalWaterLevel;
  
  /**
   * The minimum water level to still be in the normal section. 
   */
  private double minNormalWaterLevel;
  
  /**
   * The maximum limit for the water level. 
   */
  private double maxLimitWaterLevel;
  
  /**
   * The minimum limit for the water level.
   */
  private double minLimitWaterLevel;
  
  /**
   * The mid point of the water level. The ideal place to be. 
   */
  private double midLimitWaterLevel;
  
  /**
   * Boolean for if the valve if open. 
   */
  private boolean openValve;
  
  /**
   * Boolean array of the working pumps. 
   * True for a pump that works and false for a pump that doen't. 
   */
  public boolean[] workingPumps = new boolean[4];
  
  /**
   * Boolean array of the working pump controllers. 
   * True for a pump controller that works and false for a pump controller that doen't. 
   */
  public boolean[] workingPumpControllers = new boolean[4];
  
  /**
   * Boolean array for the open pumps.
   * True for pump that is open and false for a pump that isn't.
   */
  public boolean[] openPumps = new boolean[4];
  
  /**
   * Boolean for if the water level device has failed.
   */
  private boolean waterLevelDeviceFailure;
  
  /**
   * Boolean for if the steam level device has failed. 
   */
  private boolean steamLevelDeviceFailure;
  
  
  /**
   * Boolean for if the water level device needs to be repaired.
   */
  private boolean waterLevelDeviceNeedingRepair;
  
  /**
   * Boolean for if the steam level device needs to be repaired.
   */
  private boolean steamLevelDeviceNeedingRepair;
  
  /**
   * Boolean array for if any of the pumps need a repair.
   */
  public boolean[] pumpsNeedingRepair = new boolean[4];
  
  /**
   * Boolean array for if any of the pump contr0llers need a repair.
   */
  public boolean[] pumpControllersNeedingRepair = new boolean[4];
  
  /**
   * Boolean for if the water level device needs to be acknowledged that it has failed.
   */
  private boolean waterLevelDeviceNeedingAck;
  
  /**
   * Boolean for if the steam level device needs to be acknowledged that it has failed. 
   */
  private boolean steamLevelDeviceNeedingAck;
  
  /**
   * Boolean array for if any of the pumps need acknowledgement that they have failed.
   */
  public boolean[] pumpsNeedingAck = new boolean[4];
  
  /**
   * Boolean array for if any of the pump controllers need acknowledgement that they have failed. 
   */
  public boolean[] pumpControllersNeedingAck = new boolean[4];
  
  /**
   * List of the states that the controller can be in. 
   * @author Caitlin
   *
   */
  private enum State {
    /** 
     * Waiting State. State when the program starts
     * Checks that the water and steam levels are correct
     * Can move into Ready or Emergency_stop states
     */
    WAITING, 
    /**
     * Ready State. State where the program is ready to start.
     * Checks that all the parts of the boiler are ready to start
     * Can move into Normal once all the parts are read. 
     */
    READY, 
    /**
     * Normal State. State where the program is running normally.
     * Turns the pumps on and off to keep the water within the right limits
     * Can move into Degraded, rescue or Emergency_Stop if there are any problems
     */
    NORMAL, 
    /**
     * Degraded State. State where it tries to maintain water levels, 
     * even though there is an issue with a physical unit.
     * Can move into normal, if the issue is repaired
     * Can move into Rescue or Emergency stop if there are more issues.
     */
    DEGRADED, 
    
    /**
     * Rescue State. State where it tries to maintain water levels,
     * even though the water level device has failed.
     * Can move into normal, if the issue is fixed.
     * Can move into degraded. if there are other issues
     */
    RESCUE, 
    
    /**
     * Emergency Stop. State to stop if there is major issues.
     */
    EMERGENCY_STOP
  }

  /**
   * Records the configuration characteristics for the given boiler problem.
   */
  //private final SteamBoilerCharacteristics configuration;


  /**
 * Identifies the current mode in which the controller is operating.
 */
  private State mode = State.WAITING;
  
  /**
   * Stores a message that doesn't need any parameters.
   * Used to send to outgoing mailbox.
   */
  private Message messNoPara = new Message(MessageKind.VALVE);
  
  /**
   * Stores a message that needs an int parameter.
   * Used to send to outgoing mailbox.
   */
  private Message messIntPara = new Message(MessageKind.PUMP_CONTROL_FAILURE_ACKNOWLEDGEMENT_n,0);
  
  /**
   * Stores a message that needs a mode as a parameter.
   * Used to send to outgoing mailbox.
   */
  private Message messModePara = new Message(MessageKind.MODE_m, Mailbox.Mode.INITIALISATION);
  
  /**
   * Stores the messages to open the pumps.
   * Used to send to outgoing mailbox. 
   */
  public @NonNull Message[] openMessages = new @NonNull Message[4];
  
  /**
   * Stores the messages to close the pumps.
   * Used to send to outgoing mailbox. 
   */
  public @NonNull Message[] closeMessages = new @NonNull Message[4];

  /**
 * Construct a steam boiler controller for a given set of characteristics.
 *
 * @param configuration The boiler characteristics to be used.
 */
  
  public MySteamBoilerController(SteamBoilerCharacteristics configuration) {
    doInitialisation(configuration);
    
  }

  /**
   * Complete all the initialization.
   * Used to assign dynamic memory
   * @param configuration 
   */
  @Initialisation
  private void doInitialisation(SteamBoilerCharacteristics configuration) {
    this.numberOfPumps = configuration.getNumberOfPumps();
    this.pumpCapacity = configuration.getPumpCapacity(0);
    this.waterLevel = 0.0;
    this.waterCapacity = configuration.getCapacity();
    this.steamLevel = 0.0;
    this.maxSteamLevel = configuration.getMaximualSteamRate();
    this.maxNormalWaterLevel = configuration.getMaximalNormalLevel();
    this.minNormalWaterLevel = configuration.getMinimalNormalLevel();
    this.maxLimitWaterLevel = configuration.getMaximalLimitLevel();
    this.minLimitWaterLevel = configuration.getMinimalLimitLevel();
    this.midLimitWaterLevel = this.minNormalWaterLevel 
        + ((this.maxNormalWaterLevel - this.minNormalWaterLevel) / 2.0);
    this.openValve = false;
    this.waterLevelDeviceFailure = false;
    this.steamLevelDeviceFailure = false;
    this.waterLevelDeviceNeedingRepair = false;
    this.steamLevelDeviceNeedingRepair = false;
    this.pumpsNeedingRepair = new boolean[this.numberOfPumps];
    this.pumpControllersNeedingRepair = new boolean[this.numberOfPumps];
    this.waterLevelDeviceNeedingAck = false;
    this.steamLevelDeviceNeedingAck = false;
    this.pumpsNeedingAck = new boolean[this.numberOfPumps];
    this.pumpControllersNeedingAck = new boolean[this.numberOfPumps];
    this.openPumps = new boolean[this.numberOfPumps];
    this.workingPumps = new boolean[this.numberOfPumps];
    this.workingPumpControllers = new boolean[this.numberOfPumps];
    this.openMessages = new @NonNull Message[this.numberOfPumps];
    this.closeMessages = new @NonNull Message[this.numberOfPumps];
    for (int i = 0; i < this.numberOfPumps;i++) {
      this.workingPumps[i] = true;
      this.workingPumpControllers[i] = true;
      this.openMessages[i] = new Message(MessageKind.OPEN_PUMP_n,i);
      this.closeMessages[i] = new Message(MessageKind.CLOSE_PUMP_n,i);
    }
    this.messNoPara = new Message(MessageKind.VALVE);
    this.messIntPara = new Message(MessageKind.PUMP_CONTROL_FAILURE_ACKNOWLEDGEMENT_n,0);
    this.messModePara = new Message(MessageKind.MODE_m, Mailbox.Mode.INITIALISATION);
  }

  /**
 * This message is displayed in the simulation window, and enables a limited
 * form of debug output. The content of the message has no material effect on
 * the system, and can be whatever is desired. In principle, however, it should
 * display a useful message indicating the current state of the controller.
 *
 * @return status messages. 
 */
  @Override
  public String getStatusMessage() {
    String statusMessage = this.mode.toString();
    if (statusMessage != null) {
      return statusMessage;
    }
    return ""; //$NON-NLS-1$
  }

  /**
 * Process a clock signal which occurs every 5 seconds. This requires reading
 * the set of incoming messages from the physical units and producing a set of
 * output messages which are sent back to them.
 *
 * @param incoming The set of incoming messages from the physical units.
 * @param outgoing Messages generated during the execution of this method should
 *                 be written here.
 */
  @Override
  public void clock(Mailbox incoming, Mailbox outgoing) {
    // Extract expected messages
    Message levelMessage = extractOnlyMatch(MessageKind.LEVEL_v, incoming);
    Message steamMessage = extractOnlyMatch(MessageKind.STEAM_v, incoming);
    Message[] pumpStateMessages = extractAllMatches(MessageKind.PUMP_STATE_n_b, incoming);
    Message[] pumpControlStateMessages = 
        extractAllMatches(MessageKind.PUMP_CONTROL_STATE_n_b, incoming);
    if (transmissionFailure(levelMessage, steamMessage, 
        pumpStateMessages, pumpControlStateMessages)) {
      // Level and steam messages required, so emergency stop.
      this.mode = State.EMERGENCY_STOP;
    } else {
      if (levelMessage != null && steamMessage != null) {
        this.waterLevel = levelMessage.getDoubleParameter();
        this.steamLevel = steamMessage.getDoubleParameter();
      }

    }
    // FIXME: this is where the main implementation stems from
    if (this.mode == State.RESCUE) {
      boilerRescueMode(incoming,outgoing);
    } else if (this.mode == State.DEGRADED) {
      boilerDegradedMode(incoming, outgoing);
    } else if (this.mode == State.EMERGENCY_STOP) {
      boilerEmergencyStopMode(incoming,outgoing);
    } else if (this.mode == State.NORMAL) {
      boilerNormalMode(incoming,outgoing);
    } else if (this.mode == State.READY) {
      boilerReadyMode(incoming,outgoing);
    } else if (this.mode == State.WAITING) {
      boilerWaitingMode(incoming,outgoing);
    }
    
    // NOTE: this is an example message send to illustrate the syntax
    
    if (this.mode == State.RESCUE) {
      outgoing.send(this.messModePara.set(MessageKind.MODE_m,Mailbox.Mode.RESCUE));
    } else if (this.mode == State.DEGRADED) {
      outgoing.send(this.messModePara.set(MessageKind.MODE_m, Mailbox.Mode.DEGRADED));
    } else if (this.mode == State.EMERGENCY_STOP) {
      outgoing.send(this.messModePara.set(MessageKind.MODE_m, Mailbox.Mode.EMERGENCY_STOP));
    } else if (this.mode == State.NORMAL) {
      outgoing.send(this.messModePara.set(MessageKind.MODE_m, Mailbox.Mode.NORMAL));
    } else if (this.mode == State.READY) {
      outgoing.send(this.messModePara.set(MessageKind.MODE_m, Mailbox.Mode.INITIALISATION));
    }  else if (this.mode == State.WAITING) {
      outgoing.send(this.messModePara.set(MessageKind.MODE_m, Mailbox.Mode.INITIALISATION));
    }
  }
  
  /**
   * Rescue operation.
   * Boiler tries to keep functioning with water failure
   * @param incoming = incoming messages.
   * @param outgoing = outgoing messages. 
   */
  private void boilerRescueMode(Mailbox incoming, Mailbox outgoing) {
    assert incoming != null && outgoing != null;
    assert this.mode == State.RESCUE;
    
    processIncomingMessages(incoming,outgoing);
    doRepairs(incoming,outgoing);

    if (detectedWaterLevelFailure(outgoing) 
        && (detectedSteamLevelFailure(outgoing) || detectedPumpFailure(incoming,outgoing))) {
      this.mode = State.EMERGENCY_STOP;
      return;
    }
    
    if (!waterLevelInLimits()) {
      this.mode = State.EMERGENCY_STOP;
      return;
    }
    
    if (!detectedWaterLevelFailure(outgoing)) {
      if (detectedSteamLevelFailure(outgoing) 
          || detectedPumpFailure(incoming,outgoing)) {
        this.mode = State.DEGRADED;
      } else {
        this.mode = State.NORMAL;
      }
      return;
    }
    double waterIn = (this.cycle * this.pumpCapacity * getNumberOfOpenPumps());
    double maxWaterLevel = this.waterLevel + waterIn - (this.cycle * this.steamLevel);
    double minWaterLevel = this.waterLevel + waterIn - (this.cycle * this.maxSteamLevel);
    double prediction = minWaterLevel + (Math.abs(maxWaterLevel - minWaterLevel) / 2.0);
    
    this.waterLevel = prediction;
    
    if (this.waterLevel < this.midLimitWaterLevel) {
      changeNumberOpenPumps(getNumberOfOpenPumps() + 1,outgoing);
    } else {
      int toOpen = getNumberOfOpenPumps() - 1;
      if (toOpen < 0) {
        toOpen = 0;
      }
      changeNumberOpenPumps(toOpen,outgoing);
    }
  }

  /**
   * Degrading Operation. 
   * Boiler tries continue with a physical unit failure
   * @param incoming = incoming messages. 
   * @param outgoing = outgoing messages. 
   */
  public void boilerDegradedMode(Mailbox incoming, Mailbox outgoing) {
    assert incoming != null && outgoing != null;
    assert this.mode == State.DEGRADED;
    
    processIncomingMessages(incoming,outgoing);
    doRepairs(incoming,outgoing);
    
    if (detectedWaterLevelFailure(outgoing)) {
      if (detectedSteamLevelFailure(outgoing)) {
        this.mode = State.EMERGENCY_STOP;
        return;
      }
      this.mode = State.RESCUE;
      return;
    }
    
    if (!waterLevelInLimits()) {
      this.mode = State.EMERGENCY_STOP;
      return;
    }
    
    if (!detectedSteamLevelFailure(outgoing) && !detectedPumpFailure(incoming,outgoing) 
        && !detectedControllerFailure(incoming,outgoing)) {
      this.mode = State.NORMAL;
      return;
    }
    
    if (this.waterLevel < this.midLimitWaterLevel) {
      changeNumberOpenPumps(getNumberOfOpenPumps() + 1,outgoing);
    } else {
      int toOpen = getNumberOfOpenPumps() - 1;
      if (toOpen < 0) {
        toOpen = 0;
      }
      changeNumberOpenPumps(toOpen,outgoing);
    }
  }
  
  /**
   * Get the number of pumps that are open.
   * @return = number of open pumps
   */
  private int getNumberOfOpenPumps() {
    int count = 0;
    
    for (int i = 0; i < this.numberOfPumps; i++) {
      if (this.openPumps[i]) {
        count++;
      }
    }
    assert count >= 0;
    return count;
  }

  /**
   * Do repairs for the physical units. 
   * Repairs the units that are broken
   * @param incoming = incoming.
   * @param outgoing = outgoing. 
   */
  private void doRepairs(Mailbox incoming, Mailbox outgoing) {
    assert incoming != null && outgoing != null;
    
    if (this.waterLevelDeviceFailure && this.waterLevelDeviceNeedingRepair) {
      if (extractOnlyMatch(MessageKind.LEVEL_REPAIRED, incoming) != null) {
        this.waterLevelDeviceNeedingRepair = false;
        this.waterLevelDeviceFailure = false;
        outgoing.send(this.messNoPara.set(MessageKind.LEVEL_REPAIRED_ACKNOWLEDGEMENT));
      }
    }
    
    if (this.steamLevelDeviceFailure && this.steamLevelDeviceNeedingRepair) {
      if (extractOnlyMatch(MessageKind.STEAM_REPAIRED, incoming) != null) {
        this.steamLevelDeviceNeedingRepair = false;
        this.steamLevelDeviceFailure = false;
        outgoing.send(this.messNoPara.set(MessageKind.STEAM_REPAIRED_ACKNOWLEDGEMENT));
      }
    }
    
    if (countTrueValues(this.workingPumps) < this.numberOfPumps 
        && countTrueValues(this.pumpsNeedingRepair) > 0) {
      Message[] pumpMessages = extractAllMatches(MessageKind.PUMP_REPAIRED_n,incoming);
      
      for (int i = 0; i < pumpMessages.length; i++) {
        this.pumpsNeedingRepair[pumpMessages[i].getIntegerParameter()] = false;
        this.workingPumps[pumpMessages[i].getIntegerParameter()] = true;
        outgoing.send(this.messIntPara.set(
            MessageKind.PUMP_REPAIRED_ACKNOWLEDGEMENT_n,pumpMessages[i].getIntegerParameter()));
        
      }
    }
    
    if (countTrueValues(this.workingPumpControllers) < this.numberOfPumps 
        && countTrueValues(this.pumpControllersNeedingRepair) > 0) {
      Message[] pumpControllersMessages = 
          extractAllMatches(MessageKind.PUMP_CONTROL_REPAIRED_n,incoming);
      
      for (int i = 0; i < pumpControllersMessages.length; i++) {
        this.pumpControllersNeedingRepair
        [pumpControllersMessages[i].getIntegerParameter()] = false;
        this.workingPumpControllers
        [pumpControllersMessages[i].getIntegerParameter()] = true;
        outgoing.send(this.messIntPara.set(MessageKind.PUMP_CONTROL_REPAIRED_ACKNOWLEDGEMENT_n,
            pumpControllersMessages[i].getIntegerParameter()));
        
      }
    }
  }

  /**
   * Process the messages that have come from the parts.
   * Checks which devices have failures
   * @param incoming = incoming messages;
   * @param outgoing = outgoing messages;
   */
  private void processIncomingMessages(Mailbox incoming, Mailbox outgoing) {
    assert incoming != null && outgoing != null;
    
    if (this.waterLevelDeviceFailure && this.waterLevelDeviceNeedingAck) {
      if (extractOnlyMatch(MessageKind.LEVEL_FAILURE_ACKNOWLEDGEMENT,incoming) != null) {
        this.waterLevelDeviceNeedingAck = false;
        this.waterLevelDeviceNeedingRepair = true;
      } else {
        outgoing.send(this.messNoPara.set(MessageKind.LEVEL_FAILURE_DETECTION));
      }
    }
    
    if (this.steamLevelDeviceFailure && this.steamLevelDeviceNeedingAck) {
      if (extractOnlyMatch(MessageKind.STEAM_OUTCOME_FAILURE_ACKNOWLEDGEMENT,incoming) != null) {
        this.steamLevelDeviceNeedingAck = false;
        this.steamLevelDeviceNeedingRepair = true;
      } else {
        outgoing.send(this.messNoPara.set(MessageKind.STEAM_FAILURE_DETECTION));
      }
    }
    
    //If there is at least one pump that has failed
    if (countTrueValues(this.workingPumps) < this.numberOfPumps) {
      Message[] pumpMessages = 
          extractAllMatches(MessageKind.PUMP_FAILURE_ACKNOWLEDGEMENT_n,incoming);
      if (pumpMessages.length > 0) {
        for (int i = 0; i < pumpMessages.length; i++) {
          this.pumpsNeedingAck[pumpMessages[i].getIntegerParameter()] = false;
          this.pumpsNeedingRepair[pumpMessages[i].getIntegerParameter()] = true;
        }
      } else {
        for (int i = 0; i < this.numberOfPumps; i++) {
          if (!this.workingPumps[i] 
              && this.pumpsNeedingAck[i]) {
            outgoing.send(this.messIntPara.set(MessageKind.PUMP_FAILURE_DETECTION_n,i));
          }
        }
      }
    }
    
    if (countTrueValues(this.workingPumpControllers) < this.numberOfPumps) {
      Message[] pumpControllerMessages = 
          extractAllMatches(MessageKind.PUMP_CONTROL_FAILURE_ACKNOWLEDGEMENT_n,incoming);
      if (pumpControllerMessages.length > 0) {
        for (int i = 0; i < pumpControllerMessages.length; i++) {
          this.pumpControllersNeedingAck
          [pumpControllerMessages[i].getIntegerParameter()] = false;
          this.pumpControllersNeedingRepair
          [pumpControllerMessages[i].getIntegerParameter()] = true;
        }
      } else {
        for (int i = 0; i < this.numberOfPumps; i++) {
          if (!this.workingPumpControllers[i] 
              && this.pumpControllersNeedingAck[i]) {
            outgoing.send(this.messIntPara.set(MessageKind.PUMP_CONTROL_FAILURE_DETECTION_n,i));
          }
        }
      }
    }
  }
  
  /**
   * Count the number of true values in an array list. 
   * @param list = list of count true values from. 
   * @return = number of true values. 
   */
  private static int countTrueValues(boolean[] list) {
    assert list != null;
    int count = 0;
    for (int i = 0; i < list.length; i++) {
      if (list[i]) {
        count++;
      }
    }
    assert count >= 0;
    return count;
  }

  /**
   * Do Emergency Stop operation. 
   * Stops the boiler as something has gone wrong
   * @param incoming = incoming messages.
   * @param outgoing = outgoing messages. 
   */
  public void boilerEmergencyStopMode(Mailbox incoming, Mailbox outgoing) {
    assert incoming != null && outgoing != null;
    assert this.mode == State.EMERGENCY_STOP;
    
    changeNumberOpenPumps(0,outgoing);
    
    if (!this.openValve) {
      outgoing.send(this.messNoPara.set(MessageKind.VALVE));
      this.openValve = true;
    }
  }
  
  /**
   * Do ready operation.
   * Wait for the physical units to be ready
   * @param incoming = incoming messages.
   * @param outgoing = incoming messages. 
   */
  public void boilerReadyMode(Mailbox incoming, Mailbox outgoing) {
    assert incoming != null && outgoing != null;
    assert this.mode == State.READY;
    if (extractOnlyMatch(MessageKind.PHYSICAL_UNITS_READY,incoming) != null) {
      this.mode = State.NORMAL;
    } else {
      outgoing.send(this.messNoPara.set(MessageKind.PROGRAM_READY));
    }
  }
  
  /**
   * Does the normal operation. 
   * Boiler runs normally until it founds a device failure
   * @param incoming = incoming messages.
   * @param outgoing = outgoing messages. 
   */
  public void boilerNormalMode(Mailbox incoming, Mailbox outgoing) {
    assert incoming != null && outgoing != null;
    assert this.mode == State.NORMAL;
    
    if (!detectedWaterLevelFailure(outgoing) && !waterLevelInLimits()) {
      this.mode = State.EMERGENCY_STOP;
      return;
    } else if (detectedWaterLevelFailure(outgoing) && detectedSteamLevelFailure(outgoing)) {
      this.mode = State.EMERGENCY_STOP;
      return;
    }
    
    if (detectedWaterLevelFailure(outgoing)) {
      this.mode = State.RESCUE;
    } else if (detectedSteamLevelFailure(outgoing) || detectedControllerFailure(incoming,outgoing) 
        || detectedPumpFailure(incoming, outgoing)) {
      this.mode = State.DEGRADED;
      return;
    }
    
    changeNumberOpenPumps(predictNumberOfPumpsToOpen(),outgoing);
  }
  
  /**
   * Detect if there is a pump failure.
   * @param incoming = incoming messages.
   * @param outgoing = outgoing messages.
   * @return = if there if a pump failure. 
   */
  private boolean detectedPumpFailure(Mailbox incoming, Mailbox outgoing) {
    assert incoming != null && outgoing != null;
    Message[] pumpMessages = extractAllMatches(MessageKind.PUMP_STATE_n_b, incoming);

    for (int i = 0; i < this.numberOfPumps; i++) {
      if (this.workingPumps[i]) {
        if (this.openPumps[i] != pumpMessages[i].getBooleanParameter()) {
          if (this.openPumps[i]) {
            this.openPumps[i] = false;
          } else {
            this.openPumps[i] = true;
          }
          this.workingPumps[i] = false;
          this.pumpsNeedingAck[i] = true;
          outgoing.send(this.messIntPara.set(MessageKind.PUMP_FAILURE_DETECTION_n,i));
          return true;
        }
      }
      
      if (countTrueValues(this.pumpsNeedingAck) > 0) {
        return true;
      } else if (countTrueValues(this.pumpsNeedingRepair) > 0) {
        return true;
      }
    }
    return false;
  }

  /**
   * Detect if there is a controller failure.
   * @param incoming = incoming messages.
   * @param outgoing = outgoing messages. 
   * @return = if there is a controller failure. 
   */
  private boolean detectedControllerFailure(Mailbox incoming, Mailbox outgoing) {
    assert incoming != null && outgoing != null;
    Message[] pumpMessages = extractAllMatches(MessageKind.PUMP_STATE_n_b, incoming);
    Message[] pumpControllerMessages = 
        extractAllMatches(MessageKind.PUMP_CONTROL_STATE_n_b, incoming);
    
    for (int i = 0; i < this.numberOfPumps; i++) {
      if (this.openPumps[i] == pumpMessages[i].getBooleanParameter()) {
        if (this.openPumps[i] 
            != pumpControllerMessages[i].getBooleanParameter()) {
          this.workingPumpControllers[i] = false;
          this.pumpControllersNeedingAck[i] = true;
          outgoing.send(this.messIntPara.set(MessageKind.PUMP_CONTROL_FAILURE_DETECTION_n,i));
          return true;
        }
      }
      
      if (countTrueValues(this.pumpControllersNeedingAck) > 0) {
        return true;
      } else if (countTrueValues(this.pumpControllersNeedingRepair) > 0) {
        return true;
      }
    }
    return false;
  }

  /**
   * Check that if the stream device has failed.
   * @param outgoing = outgoing messages. 
   * @return = if there if a steam level failure
   */
  private boolean detectedSteamLevelFailure(Mailbox outgoing) {
    assert outgoing != null;
    if (this.steamLevel > this.maxSteamLevel || this.steamLevel < 0) {
      outgoing.send(this.messNoPara.set(MessageKind.STEAM_FAILURE_DETECTION));
      this.steamLevelDeviceFailure = true;
      this.steamLevelDeviceNeedingAck = true;
      return true;
    }
    return false;
  }

  /**
   * Check if the water level is within the limits.
   * @return = if the water is with the limits
   */
  private boolean waterLevelInLimits() {
    if (this.waterLevel > this.minLimitWaterLevel && this.waterLevel < this.maxLimitWaterLevel) {
      return true; 
    }
    return false;
  }

  /**
   * Check if the water level device has found a failure.
   * @param outgoing = outgoing messages.
   * @return = if there is a water level failure. 
   */
  private boolean detectedWaterLevelFailure(Mailbox outgoing) {
    assert outgoing != null;
    if (this.waterLevel < 0 || this.waterLevel >= this.waterCapacity) {
      outgoing.send(this.messNoPara.set(MessageKind.LEVEL_FAILURE_DETECTION));
      this.waterLevelDeviceFailure = true;
      this.waterLevelDeviceNeedingAck = true;
      return true;
    }
    return false;
  }

  /**
   * Does the waiting operation. 
   * Make sure that everything is working and get the water to the right level
   * @param incoming = incoming messages. 
   * @param outgoing = outgoing messages. 
   */
  public void boilerWaitingMode(Mailbox incoming, Mailbox outgoing) {
    assert incoming != null && outgoing != null;
    assert this.mode == State.WAITING;
    if (extractOnlyMatch(MessageKind.STEAM_BOILER_WAITING,incoming) == null) {
      return;
    }
    
    if (this.waterLevel < 0 || this.waterLevel > this.waterCapacity 
        || this.steamLevel != 0 || detectedWaterLevelFailure(outgoing)) {
      this.mode = State.EMERGENCY_STOP;
      return;
    }
    
    
    if (this.waterLevel > this.maxNormalWaterLevel) {
      if (!this.openValve) {
        outgoing.send(this.messNoPara.set(MessageKind.VALVE));
        this.openValve = true;
      }
    } else if (this.waterLevel < this.minNormalWaterLevel) {
      changeNumberOpenPumps(predictNumberOfPumpsToOpen(),outgoing);
      
      if (this.openValve) {
        outgoing.send(this.messNoPara.set(MessageKind.VALVE));
        this.openValve = false;
      }
    } else {
      changeNumberOpenPumps(0,outgoing);
      outgoing.send(this.messNoPara.set(MessageKind.PROGRAM_READY));
      this.mode = State.READY;
    } 
  }
  
  /**
   * Predict how many pumps should be open to keep the water within the limits
   * Calculate where the water would be for each option (0,1...) 
   * @return = the number of open pumps that keep it closest to the middle
   */
  private int predictNumberOfPumpsToOpen() {
    int numberToOpen = 0;
    double closestToNormal = Double.MAX_VALUE;
    for (int i = 0;i <= this.numberOfPumps;i++) {
      double waterIn = (this.cycle * this.pumpCapacity * i);
      double maxWaterLevel = this.waterLevel + waterIn - (this.cycle * this.steamLevel);
      double minWaterLevel = this.waterLevel + waterIn - (this.cycle * this.maxSteamLevel);
      double prediction = minWaterLevel + (Math.abs(maxWaterLevel - minWaterLevel) / 2.0);
      double diff = Math.abs(this.midLimitWaterLevel - prediction);
      
      if (diff < closestToNormal) {
        closestToNormal = diff;
        numberToOpen = i;
      }
      
    }
    assert numberToOpen >= 0 && numberToOpen <= this.numberOfPumps;
    return numberToOpen;
  }
  
  /**
   * Open a given number of pumps. 
   * Open how many should be open and then close the rest.
   * @param numberPumpsToOpen = the number of pumps that should be open.
   * @param outgoing = outgoing messages. 
   */
  private void changeNumberOpenPumps(int numberPumpsToOpen, Mailbox outgoing) {
    assert outgoing != null;
    assert numberPumpsToOpen >= 0;
    int counter = 0;
    for (int i = 0; i < this.numberOfPumps; i++) {
      if (counter < numberPumpsToOpen) {
        if (this.openPumps[i]) {
          counter++;
        } else if (this.workingPumps[i]) {
          outgoing.send(this.openMessages[i]);
          this.openPumps[i] = true;
          counter++;
        }
      } else {
        if (this.openPumps[i]) {
          outgoing.send(this.closeMessages[i]);
          this.openPumps[i] = false;
        }
      }
    }
  }
  
  /**
   * Check whether there was a transmission failure. This is indicated in several
   * ways. Firstly, when one of the required messages is missing. Secondly, when
   * the values returned in the messages are nonsensical.
   *
   * @param levelMessage      Extracted LEVEL_v message.
   * @param steamMessage      Extracted STEAM_v message.
   * @param pumpStates        Extracted PUMP_STATE_n_b messages.
   * @param pumpControlStates Extracted PUMP_CONTROL_STATE_n_b messages.
   * @return = if there is a transmission failure. 
   */
  private boolean transmissionFailure(@Nullable Message levelMessage, 
      @Nullable Message steamMessage, Message[] pumpStates,
      Message[] pumpControlStates) {
    // Check level readings
    if (levelMessage == null) {
      // Nonsense or missing level reading
      return true;
    } else if (steamMessage == null) {
      // Nonsense or missing steam reading
      return true;
    } else if (pumpStates.length != this.numberOfPumps) {
      // Nonsense pump state readings
      return true;
    } else if (pumpControlStates.length != this.numberOfPumps) {
      // Nonsense pump control state readings
      return true;
    }
    // Done
    return false;
  }
  
  /**
   * Find and extract a message of a given kind in a mailbox. This must the only
   * match in the mailbox, else <code>null</code> is returned.
   *
   * @param kind     The kind of message to look for.
   * @param incoming The mailbox to search through.
   * @return The matching message, or <code>null</code> if there was not exactly
   *         one match.
   */
  private static @Nullable Message extractOnlyMatch(MessageKind kind, Mailbox incoming) {
    Message match = null;
    for (int i = 0; i != incoming.size(); ++i) {
      Message ith = incoming.read(i);
      if (ith.getKind() == kind) {
        if (match == null) {
          match = ith;
        } else {
          // This indicates that we matched more than one message of the given kind.
          return null;
        }
      }
    }
    return match;
  }
  
  /**
   * Find and extract all messages of a given kind.
   *
   * @param kind     The kind of message to look for.
   * @param incoming The mailbox to search through.
   * @return The array of matches, which can empty if there were none.
   */
  private static Message[] extractAllMatches(MessageKind kind, Mailbox incoming) {
    int count = 0;
    // Count the number of matches
    for (int i = 0; i != incoming.size(); ++i) {
      Message ith = incoming.read(i);
      if (ith.getKind() == kind) {
        count = count + 1;
      }
    }
    // Now, construct resulting array
    Message[] matches = new Message[count];
    int index = 0;
    for (int i = 0; i != incoming.size(); ++i) {
      Message ith = incoming.read(i);
      if (ith.getKind() == kind) {
        matches[index++] = ith;
      }
    }
    return matches;
  }
}
