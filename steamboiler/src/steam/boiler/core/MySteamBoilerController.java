package steam.boiler.core;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import steam.boiler.model.SteamBoilerController;
import steam.boiler.util.Mailbox;
import steam.boiler.util.Mailbox.Message;
import steam.boiler.util.Mailbox.MessageKind;
import steam.boiler.util.SteamBoilerCharacteristics;


public class MySteamBoilerController implements SteamBoilerController {
  
  /**
  * Captures the various modes in which the controller can operate.
  *
  * @author David J. Pearce
  */
  
  private int cycle = 5;
  private int numberOfPumps;
  private double pumpCapacity;
  private double waterLevel;
  private double previousWaterLevel;
  private double steamLevel;
  private double maxSteamLevel;
  private double maxNormalWaterLevel;
  private double minNormalWaterLevel;
  private double maxLimitWaterLevel;
  private double minLimitWaterLevel;
  private double midLimitWaterLevel;
  private boolean openValve;
  private boolean[] workingPumps;
  private boolean[] workingPumpControllers;
  private boolean[] openPumps;
  private boolean waterLevelDeviceFailure;
  private boolean steamLevelDeviceFailure;
  private boolean[] pumpFailures;
  private boolean[] pumpControllersFailures;
  private boolean waterLevelDeviceNeedingRepair;
  private boolean steamLevelDeviceNeedingRepair;
  private boolean[] pumpsNeedingRepair;
  private boolean[] pumpControllersNeedingRepair;
  private boolean waterLevelDeviceNeedingAck;
  private boolean steamLevelDeviceNeedingAck;
  private boolean[] pumpsNeedingAck;
  private boolean[] pumpControllersNeedingAck;
  
  private enum State {
    WAITING, READY, NORMAL, DEGRADED, RESCUE, EMERGENCY_STOP
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
 * @return
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
    if (mode == State.DEGRADED) {
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
    
    if (mode == State.DEGRADED) {
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
   * Degrading Operation. 
   * @param incoming = incoming messages. 
   * @param outgoing = outgoing messages. 
   */
  public void degrade(Mailbox incoming, Mailbox outgoing) {
    assert incoming != null && outgoing != null;
    assert mode == State.DEGRADED;
    
    processIncomingMessages(incoming,outgoing);
    doRepairs(incoming,outgoing);
    
    if (waterLevel < midLimitWaterLevel) {
      changeNumberOpenPumps(getNumberOfOpenPumps() + 1,outgoing);
    } else {
      int toOpen = getNumberOfOpenPumps() - 1;
      if (toOpen < 0) {
        toOpen = 0;
      }
      changeNumberOpenPUmps(toOpen,outgoing);
    }
    
    previousWaterLevel = waterLevel;
  }
  
  /**
   * Get the number of pumps that are open.
   * @return
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
    if (waterLevel > maxNormalWaterLevel) {
      changeNumberOpenPumps(0,outgoing);
    } else {
      changeNumberOpenPumps(predictNumberOfPumpsToOpen(),outgoing);
    }
    previousWaterLevel = waterLevel;
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
   * @return
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
