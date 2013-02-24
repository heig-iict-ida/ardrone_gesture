package madsdf.ardrone;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;

/**
 * Enumeration class describing a possible action for the drone
 * 
 * Java version : JDK 1.6.0_21
 * IDE : Netbeans 7.1.1
 * 
 * @author Gregoire Aubert
 * @version 1.0
 */
public enum ActionCommand
{
   TAKEOFF,
   LAND,
   HOVER,
   GOTOP,
   GODOWN,
   GOFORWARD,
   GOBACKWARD,
   GOLEFT,
   GORIGHT,
   ROTATELEFT,
   ROTATERIGHT,
   CHANGEVIDEO,
   NOTHING;
   
   public static ImmutableSet<ActionCommand> allCommandMask() {
       return new ImmutableSet.Builder<ActionCommand>()
               .add(ActionCommand.values())
               .build();
   }
   
   // Map ActionCommand.ordinal() to ActionCommand.name()
   public static final ImmutableSortedMap<Integer, String> ordinalToName;
   static {
       ImmutableSortedMap.Builder<Integer, String> b = ImmutableSortedMap.naturalOrder();
       for (ActionCommand a : ActionCommand.values()) {
           b.put(a.ordinal(), a.name());
       }
       ordinalToName = b.build();
   }
}
