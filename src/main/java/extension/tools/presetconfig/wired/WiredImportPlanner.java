package extension.tools.presetconfig.wired;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class WiredImportPlanner {
   private WiredImportPlanner() { }

   /** Put accessor variables before pointer/echo/reference definitions which consume them. */
   public static List<PresetWiredVariable> orderVariables(List<PresetWiredVariable> variables) {
      Set<String> defined = variables.stream().map(v -> v.variableId)
         .filter(PresetWiredBase::isLocalVariableId).collect(Collectors.toSet());
      Set<String> resolved = new HashSet<>();
      List<PresetWiredVariable> remaining = new ArrayList<>(variables);
      List<PresetWiredVariable> ordered = new ArrayList<>();
      while (!remaining.isEmpty()) {
         boolean progress = false;
         for (int i = 0; i < remaining.size();) {
            PresetWiredVariable variable = remaining.get(i);
            if (variable.getVariableIds().stream().noneMatch(id -> defined.contains(id) && !variable.getExternalVariableIds().contains(id) && !resolved.contains(id))) {
               ordered.add(variable);
               if (variable.variableId != null) resolved.add(variable.variableId);
               remaining.remove(i);
               progress = true;
            } else i++;
         }
         if (!progress) {
            throw new IllegalArgumentException("Cyclic WIRED variable dependencies: " +
               remaining.stream().map(PresetWiredVariable::getVariableName).collect(Collectors.joining(", ")));
         }
      }
      return ordered;
   }
}
