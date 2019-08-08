package org.obolibrary.robot;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Implements the validate operation for a given CSV file and ontology.
 *
 * @author <a href="mailto:consulting@michaelcuffaro.com">Michael E. Cuffaro</a>
 */
public class ValidateOperation {
  /** Logger */
  private static final Logger logger = LoggerFactory.getLogger(ValidateOperation.class);

  /** Output writer */
  private static Writer writer;

  /** The reasoner factory to use for validation */
  private static OWLReasonerFactory reasonerFactory;

  /** The ontology to use for validation */
  private static OWLOntology ontology;

  /** A map from rdfs:labels to IRIs */
  private static Map<String, IRI> labelToIriMap;

  /** A map from IRIs to rdfs:labels */
  private static Map<IRI, String> iriToLabelMap;

  /**
   * INSERT DOC HERE
   *
   * @param csvData a list of rows extracted from a CSV file to be validated
   */
  public static void validate(
      List<List<String>> csvData,
      OWLOntology ontology,
      OWLReasonerFactory reasonerFactory,
      Writer writer) throws Exception, IOException {

    // Initialize the shared variables:
    initialize(ontology, reasonerFactory, writer);

    // Create a new reasoner, from the reasoner factory, based on the ontology data:
    OWLReasoner reasoner = ValidateOperation.reasonerFactory.createReasoner(ValidateOperation.ontology);

    // Extract the header and rules rows from the CSV data and map the column names to their
    // associated rules:
    List<String> header = csvData.remove(0);
    List<String> allRules = csvData.remove(0);
    HashMap<String, Map<String, String>> headerToRuleMap = new HashMap();
    for (int i = 0; i < header.size(); i++) {
      headerToRuleMap.put(header.get(i), parse_rules(i, allRules.get(i)));
    }

    // Validate the data rows:
    for (int rowIndex = 0; rowIndex < csvData.size(); rowIndex++) {
      List<String> row = csvData.get(rowIndex);
      for (int colIndex = 0; colIndex < header.size(); colIndex++) {
        String colName = header.get(colIndex);
        Map<String, String> colRules = headerToRuleMap.get(colName);

        // Get the contents of the current cell (the 'child data')
        String childCell = row.get(colIndex).trim();
        if (childCell.equals("")) continue;

        // Get the rdfs:label and IRI corresponding to the child:
        String childLabel = get_label_from_term(childCell);
        if (childLabel == null) {
          writeout(
              "Could not find '" + childCell + "' in ontology", rowIndex, colIndex);
          continue;
        }
        IRI child = ValidateOperation.labelToIriMap.get(childLabel);
        logger.info("Found child: " + child.toString() + " with label: " + childLabel);

        // Perform further validation depending on any rules that have been defined for this column:
        if (colRules.containsKey("sc")) {
          validate_ancestry(
              child, childLabel, colRules.get("sc"), reasoner, row, rowIndex, colIndex);
        }

        if (colRules.containsKey("same-as")) {
          validate_twin_cells(
              child, childLabel, colRules.get("same-as"), reasoner, row, rowIndex, colIndex);
        }
      }
    }
    reasoner.dispose();
  }

  /**
   * INSERT DOC HERE
   */
  private static void initialize(
      OWLOntology ontology,
      OWLReasonerFactory reasonerFactory,
      Writer writer) {

    ValidateOperation.ontology = ontology;
    ValidateOperation.reasonerFactory = reasonerFactory;
    ValidateOperation.writer = writer;

    // Extract from the ontology two maps from rdfs:labels to IRIs and vice versa:
    ValidateOperation.iriToLabelMap = OntologyHelper.getIRILabels(ValidateOperation.ontology);
    ValidateOperation.labelToIriMap = reverse_iri_label_map(ValidateOperation.iriToLabelMap);
  }

  /**
   * INSERT DOC HERE
   */
  private static void writeout(String msg, int rowIndex, int colIndex) throws IOException {
    writer.write(String.format("At row: %d, column: %d: %s\n", rowIndex + 1, colIndex + 1, msg));
  }

  /**
   * INSERT DOC HERE
   */
  private static Map<String, IRI> reverse_iri_label_map(Map<IRI, String> source) {
    HashMap<String, IRI> target = new HashMap();
    for (Map.Entry<IRI, String> entry : source.entrySet()) {
      String reverseKey = entry.getValue();
      IRI reverseValue = entry.getKey();
      if (target.containsKey(reverseKey)) {
        logger.warn(
            String.format(
                "Duplicate rdfs:label '%s'. Overwriting value '%s' with '%s'",
                reverseKey, target.get(reverseKey), reverseValue));
      }
      target.put(reverseKey, reverseValue);
    }
    return target;
  }

  /**
   * INSERT DOC HERE
   */
  private static Map<String, String> parse_rules(int colIndex, String ruleString) {
    HashMap<String, String> ruleMap = new HashMap();
    String[] rules = ruleString.split("\\s*;\\s*");
    for (String rule : rules) {
      String[] ruleParts = rule.split("\\s*:\\s*", 2);
      String ruleKey = ruleParts[0].trim();
      String ruleVal = ruleParts[1].trim();
      if (ruleMap.containsKey(ruleKey)) {
        logger.warn("Duplicate rule: '" + ruleKey + "' in column " + (colIndex + 1));
      }
      ruleMap.put(ruleKey, ruleVal);
    }
    return ruleMap;
  }

  /**
   * INSERT DOC HERE
   */
  private static String get_label_from_term(String term) {
    // If the term is already a recognised label, then just send it back:
    if (ValidateOperation.labelToIriMap.containsKey(term)) {
      return term;
    }

    // Check to see if the term is a recognised IRI (possibly in short form), and if so return its
    // corresponding label:
    for (IRI iri : ValidateOperation.iriToLabelMap.keySet()) {
      if (iri.toString().equals(term) || iri.getShortForm().equals(term)) {
        return ValidateOperation.iriToLabelMap.get(iri);
      }
    }

    // If the label isn't recognised, just return null:
    return null;
  }

  /**
   * INSERT DOC HERE
   */
  private static String construct_label_from_rule(String rule, List<String> row) {
    String term = null;
    if (rule.startsWith("%")) {
      int colIndex = Integer.parseInt(rule.substring(1)) - 1;
      if (colIndex >= row.size()) {
        logger.error(
            String.format(
                "Rule: '%s' indicates a column number that is greater than the row length (%d)",
                rule, row.size()));
        return null;
      }
      term = row.get(colIndex).trim();
    }
    else {
      term = rule;
    }

    return (term != null && !term.equals("")) ? get_label_from_term(term) : null;
  }

  /**
   * INSERT DOC HERE
   */
  private static void validate_ancestry(
      IRI child,
      String childLabel,
      String parentRule,
      OWLReasoner reasoner,
      List<String> row,
      int rowIndex,
      int colIndex)
      throws Exception {

    String parentLabel = construct_label_from_rule(parentRule, row);
    if (parentLabel == null) {
      writeout(
          "Could not determine parent from rule '" + parentRule + "'", rowIndex, colIndex);
      return;
    }

    IRI parent = ValidateOperation.labelToIriMap.get(parentLabel);
    logger.info("Found parent: " + parent.toString() + " with label: " + parentLabel);

    // Get the OWLClass corresponding to the parent:
    OWLClass parentClass = OntologyHelper.getEntity(ValidateOperation.ontology, parent).asOWLClass();

    // Get the OWLClass corresponding to the child, and its super classes:
    OWLClass childClass = OntologyHelper.getEntity(ValidateOperation.ontology, child).asOWLClass();
    NodeSet<OWLClass> childAncestors = reasoner.getSuperClasses(childClass, false);

    // Check if the child's ancestors include the parent:
    if (!childAncestors.containsEntity(parentClass)) {
      writeout(
          String.format(
              "%s (%s) is not a descendant of %s (%s)\n",
              child.toString(), childLabel, parent.toString(), parentLabel),
          rowIndex, colIndex);
    }
    logger.info(
        String.format("Relationship between '%s' and '%s' is valid.", childLabel, parentLabel));
  }

  private static void validate_twin_cells(
      IRI jacob,
      String jacobLabel,
      String esauRule,
      OWLReasoner reasoner,
      List<String> row,
      int rowIndex,
      int colIndex) throws IOException {

    String esauLabel = construct_label_from_rule(esauRule, row);
    if (esauLabel == null) {
      writeout(
          "Could not determine twin cell from rule '" + esauRule + "'", rowIndex, colIndex);
      return;
    }

    IRI esau = ValidateOperation.labelToIriMap.get(esauLabel);
    if (!esau.equals(jacob)) {
      writeout(
          String.format(
              "Cell's IRI: %s (%s) does not match IRI: %s (%s) inferred from rule '%s'",
              jacob.toString(), jacobLabel, esau.toString(), esauLabel, esauRule),
          rowIndex, colIndex);
    }

    logger.info(
        String.format(
            "Validated that the content identified by '%s' identifies the same entity as '%s'",
            esauRule, jacob.toString()));
  }
}