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
  private boolean[] openPumps;
  
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
    midLimitWaterLevel = minNormalWaterLevel + ((maxNormalWaterLevel - minNormalWaterLevel)/2.0);
    openValve = false;
    openPumps = new boolean[numberOfPumps];
    workingPumps = new boolean[numberOfPumps];
    for (int i = 0; i < numberOfPumps;i++) {
      workingPumps[i] = true;
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
    }
    // FIXME: this is where the main implementation stems from
    if (this.mode == State.WAITING) {
      waiting(incoming,outgoing);
    }
    
    // NOTE: this is an example message send to illustrate the syntax
    
    if (mode == State.READY) {
      System.out.println("fkjdslk");
      outgoing.send(new Message(MessageKind.MODE_m, Mailbox.Mode.INITIALISATION));
    }
  }
  
  /**
   * Does the waiting operation. 
   * @param incoming = incoming messages. 
   * @param outgoing = outgoing messages. 
   */
  public void waiting(Mailbox incoming, Mailbox outgoing) {
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
    for (int i = 0;i < numberOfPumps;i++) {
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
