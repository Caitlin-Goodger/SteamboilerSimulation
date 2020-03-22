package steam.boiler.core;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import steam.boiler.model.SteamBoilerController;
import steam.boiler.util.Mailbox;
import steam.boiler.util.Mailbox.Message;
import steam.boiler.util.Mailbox.MessageKind;
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
   * The water level the previous cycle. 
   */
  private double previousWaterLevel;
  
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
  private boolean[] workingPumps;
  
  /**
   * Boolean array of the working pump controllers. 
   * True for a pump controller that works and false for a pump controller that doen't. 
   */
  private boolean[] workingPumpControllers;
  
  /**
   * Boolean array for the open pumps.
   * True for pump that is open and false for a pump that isn't.
   */
  private boolean[] openPumps;
  
  /**
   * Boolean for if the water level device has failed.
   */
  private boolean waterLevelDeviceFailure;
  
  /**
   * Boolean for if the steam level device has failed. 
   */
  private boolean steamLevelDeviceFailure;
  
  /**
   * Boolean array for if any of the pumps have failed.
   * True for failed, false for not. 
   */
  private boolean[] pumpFailures;
  
  /**
   * Boolean array for if any of the pump controllers have failed.
   * True for failed, false for not. 
   */
  private boolean[] pumpControllersFailures;
  
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
  private boolean[] pumpsNeedingRepair;
  
  /**
   * Boolean array for if any of the pump contr0llers need a repair.
   */
  private boolean[] pumpControllersNeedingRepair;
  
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
  private boolean[] pumpsNeedingAck;
  
  /**
   * Boolean array for if any of the pump controllers need acknowledgement that they have failed. 
   */
  private boolean[] pumpControllersNeedingAck;
  
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
  private final SteamBoilerCharacteristics configuration;

  

  /**
 * Identifies the current mode in which the controller is operating.
 */
  private State mode = State.WAITING;

  /**
 * Construct a steam boiler controller for a given set of characteristics.
 *
 * @param configuration The boiler characteristics to be used.
 */
  public MySteamBoilerController(SteamBoilerCharacteristics configuration) {
    this.configuration = configuration;
    numberOfPumps = configuration.getNumberOfPumps();
    pumpCapacity = configuration.getPumpCapacity(0);
    waterLevel = 0.0;
    waterCapacity = configuration.getCapacity();
    previousWaterLevel = 0.0;
    steamLevel = 0.0;
    maxSteamLevel = configuration.getMaximualSteamRate();
    maxNormalWaterLevel = configuration.getMaximalNormalLevel();
    minNormalWaterLevel = configuration.getMinimalNormalLevel();
    maxLimitWaterLevel = configuration.getMaximalLimitLevel();
    minLimitWaterLevel = configuration.getMinimalLimitLevel();
    midLimitWaterLevel = minNormalWaterLevel + ((maxNormalWaterLevel - minNormalWaterLevel) / 2.0);
    openValve = false;
    waterLevelDeviceFailure = false;
    steamLevelDeviceFailure = false;
    pumpFailures = new boolean[numberOfPumps];
    pumpControllersFailures = new boolean[numberOfPumps];
    waterLevelDeviceNeedingRepair = false;;
    steamLevelDeviceNeedingRepair = false;
    pumpsNeedingRepair = new boolean[numberOfPumps];
    pumpControllersNeedingRepair = new boolean[numberOfPumps];
    waterLevelDeviceNeedingAck = false;
    steamLevelDeviceNeedingAck = false;
    pumpsNeedingAck = new boolean[numberOfPumps];
    pumpControllersNeedingAck = new boolean[numberOfPumps];
    openPumps = new boolean[numberOfPumps];
    workingPumps = new boolean[numberOfPumps];
    workingPumpControllers = new boolean[numberOfPumps];
    for (int i = 0; i < numberOfPumps;i++) {
      workingPumps[i] = true;
      workingPumpControllers[i] = true;
    }
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
    return mode.toString();
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
      waterLevel = extractOnlyMatch(MessageKind.LEVEL_v,incoming).getDoubleParameter();
      steamLevel = extractOnlyMatch(MessageKind.STEAM_v,incoming).getDoubleParameter();
      
    }
    // FIXME: this is where the main implementation stems from
    if (mode == State.RESCUE) {
      rescue(incoming,outgoing);
    } else if (mode == State.DEGRADED) {
      degrade(incoming, outgoing);
    } else if (mode == State.EMERGENCY_STOP) {
      emergencyStop(incoming,outgoing);
    } else if (mode == State.NORMAL) {
      normal(incoming,outgoing);
    } else if (mode == State.READY) {
      ready(incoming,outgoing);
    } else if (this.mode == State.WAITING) {
      waiting(incoming,outgoing);
    }
    
    // NOTE: this is an example message send to illustrate the syntax
    
    if (mode == State.RESCUE) {
      outgoing.send(new Message(MessageKind.MODE_m,Mailbox.Mode.RESCUE));
    } else if (mode == State.DEGRADED) {
      outgoing.send(new Message(MessageKind.MODE_m, Mailbox.Mode.DEGRADED));
    } else if (mode == State.EMERGENCY_STOP) {
      outgoing.send(new Message(MessageKind.MODE_m, Mailbox.Mode.EMERGENCY_STOP));
    } else if (mode == State.NORMAL) {
      outgoing.send(new Message(MessageKind.MODE_m, Mailbox.Mode.NORMAL));
    } else if (mode == State.READY) {
      outgoing.send(new Message(MessageKind.MODE_m, Mailbox.Mode.INITIALISATION));
    }  else if (mode == State.WAITING) {
      outgoing.send(new Message(MessageKind.MODE_m, Mailbox.Mode.INITIALISATION));
    }
  }
  
  /**
   * Rescue operation.
   * @param incoming = incoming messages.
   * @param outgoing = outgoing messages. 
   */
  private void rescue(Mailbox incoming, Mailbox outgoing) {
    assert incoming != null && outgoing != null;
    assert mode == State.RESCUE;
    
    processIncomingMessages(incoming,outgoing);
    doRepairs(incoming,outgoing);
    
    if (detectedPumpFailure(incoming,outgoing) || detectedControllerFailure(incoming, outgoing) 
        || detectedSteamLevelFailure(outgoing)) {
      mode = State.DEGRADED;
    } else {
      mode = State.NORMAL;
    }
    System.out.println(mode);
    if (previousWaterLevel < midLimitWaterLevel) {
      changeNumberOpenPumps(getNumberOfOpenPumps() + 1, outgoing);
    } else {
      int toOpen = getNumberOfOpenPumps() - 1;
      if (toOpen < 0) {
        toOpen = 0;
      }
      changeNumberOpenPumps(toOpen,outgoing);
    }
    
    double waterIn = (cycle * pumpCapacity * getNumberOfOpenPumps());
    double maxWaterLevel = waterLevel + waterIn - (cycle * steamLevel);
    double minWaterLevel = waterLevel + waterIn - (cycle * maxSteamLevel);
    double prediction = minWaterLevel + (Math.abs(maxWaterLevel - minWaterLevel) / 2.0);
    
    
  }

  /**
   * Degrading Operation. 
   * @param incoming = incoming messages. 
   * @param outgoing = outgoing messages. 
   */
  public void degrade(Mailbox incoming, Mailbox outgoing) {
    assert incoming != null && outgoing != null;
    assert mode == State.DEGRADED;
    
    processIncomingMessages(incoming,outgoing);
    doRepairs(incoming,outgoing);
    
    if (detectedWaterLevelFailure(outgoing)) {
      if (detectedSteamLevelFailure(outgoing)) {
        mode = State.EMERGENCY_STOP;
        return;
      }
      mode = State.RESCUE;
      return;
    }
    
    if (!waterLevelInLimits()) {
      mode = State.EMERGENCY_STOP;
      return;
    }
    
    if (!detectedSteamLevelFailure(outgoing) && !detectedPumpFailure(incoming,outgoing) 
        && !detectedControllerFailure(incoming,outgoing)) {
      mode = State.NORMAL;
      return;
    }
    
    if (waterLevel < midLimitWaterLevel) {
      changeNumberOpenPumps(getNumberOfOpenPumps() + 1,outgoing);
    } else {
      int toOpen = getNumberOfOpenPumps() - 1;
      if (toOpen < 0) {
        toOpen = 0;
      }
      changeNumberOpenPumps(toOpen,outgoing);
    }
    
    previousWaterLevel = waterLevel;
  }
  
  /**
   * Get the number of pumps that are open.
   * @return = number of open pumps
   */
  private int getNumberOfOpenPumps() {
    int count = 0;
    
    for (int i = 0; i < numberOfPumps; i++) {
      if (openPumps[i]) {
        count++;
      }
    }
    
    return count++;
  }

  /**
   * Do repairs for the physical units. 
   * @param incoming = incoming.
   * @param outgoing = outgoing. 
   */
  private void doRepairs(Mailbox incoming, Mailbox outgoing) {
    assert incoming != null && outgoing != null;
    
    if (waterLevelDeviceFailure && waterLevelDeviceNeedingRepair) {
      if (extractOnlyMatch(MessageKind.LEVEL_REPAIRED, incoming) != null) {
        waterLevelDeviceNeedingRepair = false;
        waterLevelDeviceFailure = false;
        outgoing.send(new Message(MessageKind.LEVEL_REPAIRED_ACKNOWLEDGEMENT));
      }
    }
    
    if (steamLevelDeviceFailure && steamLevelDeviceNeedingRepair) {
      if (extractOnlyMatch(MessageKind.STEAM_REPAIRED, incoming) != null) {
        steamLevelDeviceNeedingRepair = false;
        steamLevelDeviceFailure = false;
        outgoing.send(new Message(MessageKind.STEAM_REPAIRED_ACKNOWLEDGEMENT));
      }
    }
    
    if (countTrueValues(workingPumps) < numberOfPumps && countTrueValues(pumpsNeedingRepair) > 0) {
      Mailbox.Message[] pumpMessages = extractAllMatches(MessageKind.PUMP_REPAIRED_n,incoming);
      
      for (int i = 0; i < pumpMessages.length; i++) {
        pumpsNeedingRepair[pumpMessages[i].getIntegerParameter()] = false;
        workingPumps[pumpMessages[i].getIntegerParameter()] = true;
        outgoing.send(new Message(
            MessageKind.PUMP_REPAIRED_ACKNOWLEDGEMENT_n,pumpMessages[i].getIntegerParameter()));
        
      }
    }
    
    if (countTrueValues(workingPumpControllers) < numberOfPumps 
        && countTrueValues(pumpControllersNeedingRepair) > 0) {
      Mailbox.Message[] pumpControllersMessages = 
          extractAllMatches(MessageKind.PUMP_CONTROL_REPAIRED_n,incoming);
      
      for (int i = 0; i < pumpControllersMessages.length; i++) {
        pumpControllersNeedingRepair[pumpControllersMessages[i].getIntegerParameter()] = false;
        workingPumpControllers[pumpControllersMessages[i].getIntegerParameter()] = true;
        outgoing.send(new Message(MessageKind.PUMP_CONTROL_REPAIRED_ACKNOWLEDGEMENT_n,
            pumpControllersMessages[i].getIntegerParameter()));
        
      }
    }
  }

  /**
   * Process the messages that have come from the parts.
   * @param incoming = incoming messages;
   * @param outgoing = outgoing messages;
   */
  private void processIncomingMessages(Mailbox incoming, Mailbox outgoing) {
    assert incoming != null && outgoing != null;
    
    if (waterLevelDeviceFailure && waterLevelDeviceNeedingAck) {
      if (extractOnlyMatch(MessageKind.LEVEL_FAILURE_ACKNOWLEDGEMENT,incoming) != null) {
        waterLevelDeviceNeedingAck = false;
        waterLevelDeviceNeedingRepair = true;
      } else {
        outgoing.send(new Message(MessageKind.LEVEL_FAILURE_DETECTION));
      }
    }
    
    if (steamLevelDeviceFailure && steamLevelDeviceNeedingAck) {
      if (extractOnlyMatch(MessageKind.STEAM_OUTCOME_FAILURE_ACKNOWLEDGEMENT,incoming) != null) {
        steamLevelDeviceNeedingAck = false;
        steamLevelDeviceNeedingRepair = true;
      } else {
        outgoing.send(new Message(MessageKind.STEAM_FAILURE_DETECTION));
      }
    }
    
    //If there is at least one pump that has failed
    if (countTrueValues(workingPumps) < numberOfPumps) {
      Mailbox.Message[] pumpMessages = 
          extractAllMatches(MessageKind.PUMP_FAILURE_ACKNOWLEDGEMENT_n,incoming);
      if (pumpMessages.length > 0) {
        for (int i = 0; i < pumpMessages.length; i++) {
          pumpsNeedingAck[pumpMessages[i].getIntegerParameter()] = false;
          pumpsNeedingRepair[pumpMessages[i].getIntegerParameter()] = true;
        }
      } else {
        for (int i = 0; i < numberOfPumps; i++) {
          if (!workingPumps[i] && pumpsNeedingAck[i]) {
            outgoing.send(new Message(MessageKind.PUMP_FAILURE_DETECTION_n,i));
          }
        }
      }
    }
    
    if (countTrueValues(workingPumpControllers) < numberOfPumps) {
      Mailbox.Message[] pumpControllerMessages = 
          extractAllMatches(MessageKind.PUMP_CONTROL_FAILURE_ACKNOWLEDGEMENT_n,incoming);
      if (pumpControllerMessages.length > 0) {
        for (int i = 0; i < pumpControllerMessages.length; i++) {
          pumpControllersNeedingAck[pumpControllerMessages[i].getIntegerParameter()] = false;
          pumpControllersNeedingRepair[pumpControllerMessages[i].getIntegerParameter()] = true;
        }
      } else {
        for (int i = 0; i < numberOfPumps; i++) {
          if (!workingPumpControllers[i] && pumpControllersNeedingAck[i]) {
            outgoing.send(new Message(MessageKind.PUMP_CONTROL_FAILURE_DETECTION_n,i));
          }
        }
      }
    }
  }
  
  /**
   * Count the number of true values in an array list. 
   * @param list = list of count values from. 
   * @return = number of true values. 
   */
  private int countTrueValues(boolean[] list) {
    assert list != null;
    int count = 0;
    for (int i = 0; i < list.length; i++) {
      if (list[i]) {
        count++;
      }
    }
    return count;
  }

  /**
   * Do Emergency Stop operation. 
   * @param incoming = incoming messages.
   * @param outgoing = outgoing messages. 
   */
  public void emergencyStop(Mailbox incoming, Mailbox outgoing) {
    assert incoming != null && outgoing != null;
    assert mode == State.EMERGENCY_STOP;
    
    changeNumberOpenPumps(0,outgoing);
    
    if (!openValve) {
      outgoing.send(new Message(MessageKind.VALVE));
      openValve = true;
    }
  }
  
  /**
   * Do ready operation.
   * @param incoming = incoming messages.
   * @param outgoing = incoming messages. 
   */
  public void ready(Mailbox incoming, Mailbox outgoing) {
    assert incoming != null && outgoing != null;
    assert mode == State.READY;
    if (extractOnlyMatch(MessageKind.PHYSICAL_UNITS_READY,incoming) != null) {
      mode = State.NORMAL;
    } else {
      outgoing.send(new Message(MessageKind.PROGRAM_READY));
    }
  }
  
  /**
   * Does the normal operation. 
   * @param incoming = incoming messages.
   * @param outgoing = outgoing messages. 
   */
  public void normal(Mailbox incoming, Mailbox outgoing) {
    assert incoming != null && outgoing != null;
    assert mode == State.NORMAL;
    
    if (!detectedWaterLevelFailure(outgoing) && !waterLevelInLimits()) {
      mode = State.EMERGENCY_STOP;
      return;
    } else if (detectedWaterLevelFailure(outgoing) && detectedSteamLevelFailure(outgoing)) {
      mode = State.EMERGENCY_STOP;
      return;
    }
    
    if (detectedWaterLevelFailure(outgoing)) {
      mode = State.RESCUE;
    } else if (detectedSteamLevelFailure(outgoing) || detectedControllerFailure(incoming,outgoing) 
        || detectedPumpFailure(incoming, outgoing)) {
      mode = State.DEGRADED;
      return;
    }
    
    changeNumberOpenPumps(predictNumberOfPumpsToOpen(),outgoing);
    previousWaterLevel = waterLevel;
  }
  
  /**
   * Detect if there is a pump failure.
   * @param incoming = incoming messages.
   * @param outgoing = outgoing messages.
   * @return = if there if a pump failure. 
   */
  private boolean detectedPumpFailure(Mailbox incoming, Mailbox outgoing) {
    assert incoming != null && outgoing != null;
    Mailbox.Message[] pumpMessages = extractAllMatches(MessageKind.PUMP_STATE_n_b, incoming);

    for (int i = 0; i < numberOfPumps; i++) {
      if (workingPumps[i]) {
        if (openPumps[i] ^ pumpMessages[i].getBooleanParameter()) {
          if (openPumps[i]) {
            openPumps[i] = false;
          } else {
            openPumps[i] = true;
          }
          workingPumps[i] = false;
          pumpsNeedingAck[i] = true;
          outgoing.send(new Message(MessageKind.PUMP_FAILURE_DETECTION_n,i));
          return true;
        }
      }
      
      if (countTrueValues(pumpsNeedingAck) > 0) {
        return true;
      } else if (countTrueValues(pumpsNeedingRepair) > 0) {
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
    Mailbox.Message[] pumpMessages = extractAllMatches(MessageKind.PUMP_STATE_n_b, incoming);
    Mailbox.Message[] pumpControllerMessages = 
        extractAllMatches(MessageKind.PUMP_CONTROL_STATE_n_b, incoming);
    
    for (int i = 0; i < numberOfPumps; i++) {
      if (openPumps[i] == pumpMessages[i].getBooleanParameter()) {
        if (openPumps[i] != pumpControllerMessages[i].getBooleanParameter()) {
          workingPumpControllers[i] = false;
          pumpControllersNeedingAck[i] = true;
          outgoing.send(new Message(MessageKind.PUMP_CONTROL_FAILURE_DETECTION_n,i));
          return true;
        }
      }
      
      if (countTrueValues(pumpControllersNeedingAck) > 0) {
        return true;
      } else if (countTrueValues(pumpControllersNeedingRepair) > 0) {
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
    
    if (steamLevel > maxSteamLevel || steamLevel <= 0) {
      outgoing.send(new Message(MessageKind.STEAM_FAILURE_DETECTION));
      steamLevelDeviceFailure = true;
      steamLevelDeviceNeedingAck = true;
      return true;
    }
    return false;
  }

  /**
   * Check if the water level is within the limits.
   * @return = if the water is with the limits
   */
  private boolean waterLevelInLimits() {
    if (waterLevel > minLimitWaterLevel && waterLevel < maxLimitWaterLevel) {
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
    if (waterLevel < 0 || waterLevel >= waterCapacity) {
      outgoing.send(new Message(MessageKind.LEVEL_FAILURE_DETECTION));
      waterLevelDeviceFailure = true;
      waterLevelDeviceNeedingAck = true;
      return true;
    }
    return false;
  }

  /**
   * Does the waiting operation. 
   * @param incoming = incoming messages. 
   * @param outgoing = outgoing messages. 
   */
  public void waiting(Mailbox incoming, Mailbox outgoing) {
    assert incoming != null && outgoing != null;
    assert mode == State.WAITING;
    if (extractOnlyMatch(MessageKind.STEAM_BOILER_WAITING,incoming) == null) {
      return;
    }
    
    if (waterLevel < 0 || waterLevel > waterCapacity 
        || steamLevel != 0 || detectedWaterLevelFailure(outgoing)) {
      mode = State.EMERGENCY_STOP;
      return;
    }
    
    
    if (waterLevel > maxNormalWaterLevel) {
      if (!openValve) {
        outgoing.send(new Message(MessageKind.VALVE));
        openValve = true;
      }
    } else if (waterLevel < minNormalWaterLevel) {
      changeNumberOpenPumps(predictNumberOfPumpsToOpen(),outgoing);
      
      if (openValve) {
        outgoing.send(new Message(MessageKind.VALVE));
        openValve = false;
      }
    } else {
      changeNumberOpenPumps(0,outgoing);
      outgoing.send(new Message(MessageKind.PROGRAM_READY));
      mode = State.READY;
    }
    previousWaterLevel = waterLevel; 
  }
  
  /**
   * Predict how many pumps should be open to keep the water within the limits
   * Calculate where the water would be for each option (0,1...) 
   * @return = the number of open pumps that keep it closest to the middle
   */
  private int predictNumberOfPumpsToOpen() {
    int numberToOpen = 0;
    double closestToNormal = Double.MAX_VALUE;
    for (int i = 0;i <= numberOfPumps;i++) {
      double waterIn = (cycle * pumpCapacity * i);
      double maxWaterLevel = waterLevel + waterIn - (cycle * steamLevel);
      double minWaterLevel = waterLevel + waterIn - (cycle * maxSteamLevel);
      double prediction = minWaterLevel + (Math.abs(maxWaterLevel - minWaterLevel) / 2.0);
      double diff = Math.abs(midLimitWaterLevel - prediction);
      
      if (diff < closestToNormal) {
        closestToNormal = diff;
        numberToOpen = i;
      }
      
    }
    return numberToOpen;
  }
  
  /**
   * Open a given number of pumps. 
   * Open how many should be open and then close the rest.
   * @param numberPumpsToOpen = the number of pumps that should be open.
   * @param outgoing = outgoing messages. 
   */
  private void changeNumberOpenPumps(int numberPumpsToOpen, Mailbox outgoing) {
    int counter = 0;
    for (int i = 0; i < numberOfPumps; i++) {
      if (counter < numberPumpsToOpen) {
        if (openPumps[i]) {
          counter++;
        } else if (workingPumps[i]) {
          outgoing.send(new Message(MessageKind.OPEN_PUMP_n,i));
          openPumps[i] = true;
          counter++;
        }
      } else {
        if (openPumps[i]) {
          outgoing.send(new Message(MessageKind.CLOSE_PUMP_n,i));
          openPumps[i] = false;
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
  private boolean transmissionFailure(Message levelMessage, 
      Message steamMessage, Message[] pumpStates,
      Message[] pumpControlStates) {
    // Check level readings
    if (levelMessage == null) {
      // Nonsense or missing level reading
      return true;
    } else if (steamMessage == null) {
      // Nonsense or missing steam reading
      return true;
    } else if (pumpStates.length != configuration.getNumberOfPumps()) {
      // Nonsense pump state readings
      return true;
    } else if (pumpControlStates.length != configuration.getNumberOfPumps()) {
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
  private static Message extractOnlyMatch(MessageKind kind, Mailbox incoming) {
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
