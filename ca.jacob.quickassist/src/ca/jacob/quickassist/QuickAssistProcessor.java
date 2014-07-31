package ca.jacob.quickassist;

import java.util.ArrayList;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.internal.corext.dom.GenericVisitor;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.text.correction.AssistContext;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.jdt.ui.text.java.IQuickAssistProcessor;
import org.eclipse.jdt.ui.text.java.correction.ICommandAccess;
import org.eclipse.ui.IEditorPart;

import ca.jacob.quickassist.proposals.CreateTypeUsingWizardProposal;

/**
 * A plugin which provides a quick assist to provide an implementation of an interface with in
 * Eclipse JDT.
 * 
 * @author Jacob Deepak
 *
 */
@SuppressWarnings("restriction")
public class QuickAssistProcessor implements IQuickAssistProcessor {

  @Override
  public boolean hasAssists(IInvocationContext context) throws CoreException {
    ASTNode coveringNode = context.getCoveringNode();
    if (coveringNode != null) {
      return getCreateInterfaceProposal(context, coveringNode, null, null);
    }
    return false;
  }

  @Override
  public IJavaCompletionProposal[] getAssists(IInvocationContext context,
      IProblemLocation[] locations) throws CoreException {
    ASTNode coveringNode = context.getCoveringNode();
    if (coveringNode != null) {
      @SuppressWarnings("unused")
      ArrayList<ASTNode> coveredNodes = getFullyCoveredNodes(context, coveringNode);
      ArrayList<ICommandAccess> resultingCollections = new ArrayList<ICommandAccess>();
      boolean noErrorsAtLocation = noErrorsAtLocation(locations);
      if (noErrorsAtLocation) {
        getCreateInterfaceProposal(context, coveringNode, locations, resultingCollections);
      }
      return resultingCollections.toArray(new IJavaCompletionProposal[resultingCollections.size()]);
    }
    return null;
  }

  /**
   * See whether an quick assist for an implementation class is relevant in the selected context.
   * 
   * @param context the quick assist context.
   * @param node the selected token from which the quick assist is launched.
   * @param locations locations where problems found in this compilation unit.
   * @param resultingCollections the proposals created.
   * @return
   */
  private boolean getCreateInterfaceProposal(IInvocationContext context, ASTNode node,
      IProblemLocation[] locations, ArrayList<ICommandAccess> resultingCollections) {

    // continue only if this is an assist context
    if (!(context instanceof AssistContext)) {
      return false;
    }
    // this is a java editor
    IEditorPart editor = ((AssistContext) context).getEditor();
    if (!(editor instanceof JavaEditor)) {
      return false;
    }
    // an interface name or class name will be an instance 
    // of a SimpleName
    if (!(node instanceof SimpleName)) {
      return false;
    }

    // parent of the selected token needs to be a TypeDeclaration
    if (!(node.getParent() instanceof TypeDeclaration)) {
      return false;
    }
    // get the compilation unit on which the quick assist is fired.
    ICompilationUnit cu = context.getCompilationUnit();

    SimpleName name = (SimpleName) node;
    // create the proposal, add it to the results and launch
    // the JDT Class Creation Wizard
    CreateTypeUsingWizardProposal proposal =
        new CreateTypeUsingWizardProposal(cu, name, CreateTypeUsingWizardProposal.K_CLASS, null, 6);
    resultingCollections.add(proposal);

    return true;
  }

  static boolean noErrorsAtLocation(IProblemLocation[] locations) {
    if (locations != null) {
      for (int i = 0; i < locations.length; i++) {
        IProblemLocation location = locations[i];
        if (location.isError()) {
          if (IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER.equals(location.getMarkerType())
              && JavaCore.getOptionForConfigurableSeverity(location.getProblemId()) != null) {
            // continue (only drop out for severe (non-optional)
            // errors)
          } else {
            return false;
          }
        }
      }
    }
    return true;
  }

  static ArrayList<ASTNode> getFullyCoveredNodes(IInvocationContext context, ASTNode coveringNode) {
    final ArrayList<ASTNode> coveredNodes = new ArrayList<ASTNode>();
    final int selectionBegin = context.getSelectionOffset();
    final int selectionEnd = selectionBegin + context.getSelectionLength();
    coveringNode.accept(new GenericVisitor() {
      @Override
      protected boolean visitNode(ASTNode node) {
        int nodeStart = node.getStartPosition();
        int nodeEnd = nodeStart + node.getLength();
        // if node does not intersects with selection, don't visit
        // children
        if (nodeEnd < selectionBegin || selectionEnd < nodeStart) {
          return false;
        }
        // if node is fully covered, we don't need to visit children
        if (isCovered(node)) {
          ASTNode parent = node.getParent();
          if (parent == null || !isCovered(parent)) {
            coveredNodes.add(node);
            return false;
          }
        }
        // if node only partly intersects with selection, we try to find
        // fully covered children
        return true;
      }

      private boolean isCovered(ASTNode node) {
        int begin = node.getStartPosition();
        int end = begin + node.getLength();
        return begin >= selectionBegin && end <= selectionEnd;
      }
    });
    return coveredNodes;
  }

}
