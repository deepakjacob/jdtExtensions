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

  private boolean getCreateInterfaceProposal(IInvocationContext context, ASTNode node,
      IProblemLocation[] locations, ArrayList<ICommandAccess> resultingCollections) {

    if (!(context instanceof AssistContext)) {
      return false;
    }

    IEditorPart editor = ((AssistContext) context).getEditor();
    if (!(editor instanceof JavaEditor)) {
      return false;
    }

    if (!(node instanceof SimpleName)) {
      return false;
    }
    
    if (!(node.getParent() instanceof TypeDeclaration)) {
      return false;
    }
    
    ICompilationUnit cu = context.getCompilationUnit();
    
    SimpleName name = (SimpleName) node;
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
