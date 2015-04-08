package ca.jacob.quickassist.proposals;

import java.util.Arrays;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.text.correction.CorrectionMessages;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.internal.ui.wizards.NewClassCreationWizard;
import org.eclipse.jdt.internal.ui.wizards.NewElementWizard;
import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jdt.ui.text.java.correction.ChangeCorrectionProposal;
import org.eclipse.jdt.ui.wizards.NewClassWizardPage;
import org.eclipse.jdt.ui.wizards.NewTypeWizardPage;
import org.eclipse.jface.layout.PixelConverter;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Shell;

@SuppressWarnings("restriction")
public class TypeCreator extends ChangeCorrectionProposal {
	public static final int K_CLASS = 1;
	@SuppressWarnings("unused")
	private Name fNode;
	private ICompilationUnit fCompilationUnit;
	private int fTypeKind;
	private IJavaElement fTypeContainer; // IType or IPackageFragment
	private String fTypeNameWithParameters;
	private IType fCreatedType;
	private boolean fShowDialog;

	public TypeCreator(ICompilationUnit cu, Name node, int typeKind, IJavaElement typeContainer, int severity) {
		super("", null, severity, null); //$NON-NLS-1$

		fCompilationUnit = cu;
		fNode = node;
		fTypeKind = typeKind;
		fTypeContainer = typeContainer;
		// FIXME : Get the correct enclosing type / package
		// Following is a hack
		fTypeContainer = cu.getParent();
		fTypeNameWithParameters = getTypeName(typeKind, node);
		fCreatedType = null;
		String containerName = ASTNodes.getQualifier(node);
		String typeName = fTypeNameWithParameters;
		String containerLabel = BasicElementLabels.getJavaElementName(containerName);
		String typeLabel = BasicElementLabels.getJavaElementName(typeName);
		boolean isInnerType = typeContainer instanceof IType;

		setImage(JavaPluginImages.get(JavaPluginImages.IMG_OBJS_CLASS));
		if (isInnerType) {
			if (containerName.length() == 0) {
				setDisplayName(Messages
				        .format(CorrectionMessages.NewCUCompletionUsingWizardProposal_createinnerclass_description,
				                typeLabel));
			} else {
				setDisplayName(Messages
				        .format(CorrectionMessages.NewCUCompletionUsingWizardProposal_createinnerclass_intype_description,
				                new String[] { typeLabel, containerLabel }));
			}
		} else {
			if (containerName.length() == 0) {
				setDisplayName(Messages
				        .format(CorrectionMessages.NewCUCompletionUsingWizardProposal_createclass_description,
				                new StringBuilder().append(typeLabel).append("Impl").toString()));
			} else {
				setDisplayName(Messages
				        .format(CorrectionMessages.NewCUCompletionUsingWizardProposal_createclass_inpackage_description,
				                new String[] { typeLabel, containerLabel }));
			}
		}

		fShowDialog = true;
	}

	private static String getTypeName(int typeKind, Name node) {
		String name = ASTNodes.getSimpleNameIdentifier(node);
		ASTNode parent = node.getParent();
		if (parent.getLocationInParent() == ParameterizedType.TYPE_PROPERTY) {
			String typeArgBaseName = name.startsWith(String.valueOf('T')) ? String
			        .valueOf('S') : String.valueOf('T');
			int nTypeArgs = ((ParameterizedType) parent.getParent()).typeArguments().size();
			StringBuffer buf = new StringBuffer(name);
			buf.append('<');
			if (nTypeArgs == 1) {
				buf.append(typeArgBaseName);
			} else {
				for (int i = 0; i < nTypeArgs; i++) {
					if (i != 0) {
						buf.append(", "); //$NON-NLS-1$
					}
					buf.append(typeArgBaseName).append(i + 1);
				}
			}
			buf.append('>');
			return buf.toString();
		}

		return name;
	}

	@Override
	public void apply(IDocument document) {
		StructuredSelection selection = new StructuredSelection(fCompilationUnit);
		NewElementWizard wizard = createWizard(selection);
		wizard.init(JavaPlugin.getDefault().getWorkbench(), selection);

		IType createdType = null;

		if (fShowDialog) {
			Shell shell = JavaPlugin.getActiveWorkbenchShell();
			WizardDialog dialog = new WizardDialog(shell, wizard);

			PixelConverter converter = new PixelConverter(JFaceResources.getDialogFont());
			dialog.setMinimumPageSize(
			        converter.convertWidthInCharsToPixels(70),
			        converter.convertHeightInCharsToPixels(20));
			dialog.create();
			dialog.getShell().setText(CorrectionMessages.NewCUCompletionUsingWizardProposal_dialogtitle);

			if (dialog.open() == Window.OK) {
				createdType = (IType) wizard.getCreatedElement();
			}
		} else {
			wizard.addPages();
			try {
				NewTypeWizardPage page = getPage(wizard);
				page.createType(null);
				createdType = page.getCreatedType();
			} catch (CoreException e) {
				JavaPlugin.log(e);
			} catch (InterruptedException e) {
			}
		}

		if (createdType != null) {
			IJavaElement container = createdType.getParent();
			if (container instanceof ICompilationUnit) {
				container = container.getParent();
			}
			if (!container.equals(fTypeContainer)) {
				// add import
				try {
					ImportRewrite rewrite = StubUtility.createImportRewrite(fCompilationUnit, true);
					rewrite.addImport(createdType.getFullyQualifiedName('.'));
					JavaModelUtil.applyEdit(fCompilationUnit, rewrite.rewriteImports(null), false, null);
				} catch (CoreException e) {
				}
			}
			fCreatedType = createdType;
		}

	}

	private NewTypeWizardPage getPage(NewElementWizard wizard) {
		IWizardPage[] pages = wizard.getPages();
		Assert.isTrue(pages.length > 0 && pages[0] instanceof NewTypeWizardPage);
		return (NewTypeWizardPage) pages[0];
	}

	private NewElementWizard createWizard(StructuredSelection selection) {
		NewClassWizardPage page = new NewClassWizardPage();
		page.init(selection);
		configureWizardPage(page);
		return new NewClassCreationWizard(page, true);
	}

	private void configureWizardPage(NewTypeWizardPage page) {
		fillInWizardPageName(page);
	}

	private void fillInWizardPageName(NewTypeWizardPage page) {
		StringBuilder typeName = new StringBuilder();
		typeName.append(fTypeNameWithParameters);
		typeName.append("Impl");
		// allow to edit when there are type parameters
		page.setTypeName(typeName.toString(), true);

		String[] interfaces = new String[] { fTypeNameWithParameters };
		page.setSuperInterfaces(Arrays.asList(interfaces), true);

		boolean isInEnclosingType = fTypeContainer instanceof IType;
		if (isInEnclosingType) {
			page.setEnclosingType((IType) fTypeContainer, true);
		} else {
			page.setPackageFragment((IPackageFragment) fTypeContainer, true);
		}
		page.setEnclosingTypeSelection(isInEnclosingType, true);
	}

	/*
	 * @see org.eclipse.jface.text.contentassist.ICompletionProposalExtension5#
	 * getAdditionalProposalInfo (org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public Object getAdditionalProposalInfo(IProgressMonitor monitor) {
		StringBuffer buf = new StringBuffer();
		buf.append(CorrectionMessages.NewCUCompletionUsingWizardProposal_createclass_info);
		buf.append("<br>"); //$NON-NLS-1$
		buf.append("<br>"); //$NON-NLS-1$
		if (fTypeContainer instanceof IType) {
			buf.append(CorrectionMessages.NewCUCompletionUsingWizardProposal_tooltip_enclosingtype);
		} else {
			buf.append(CorrectionMessages.NewCUCompletionUsingWizardProposal_tooltip_package);
		}
		buf.append(" <b>"); //$NON-NLS-1$
		buf.append(JavaElementLabels.getElementLabel(fTypeContainer, JavaElementLabels.T_FULLY_QUALIFIED));
		buf.append("</b><br>"); //$NON-NLS-1$
		buf.append("public "); //$NON-NLS-1$
		buf.append("class <b>"); //$NON-NLS-1$

		nameToHTML(fTypeNameWithParameters, buf);

		buf.append("</b> {<br>}<br>"); //$NON-NLS-1$
		return buf.toString();
	}

	private void nameToHTML(String name, StringBuffer buf) {
		for (int i = 0; i < name.length(); i++) {
			char ch = name.charAt(i);
			if (ch == '>') {
				buf.append("&gt;"); //$NON-NLS-1$
			} else if (ch == '<') {
				buf.append("&lt;"); //$NON-NLS-1$
			} else {
				buf.append(ch);
			}
		}
	}

	public boolean isShowDialog() {
		return fShowDialog;
	}

	public void setShowDialog(boolean showDialog) {
		fShowDialog = showDialog;
	}

	public IType getCreatedType() {
		return fCreatedType;
	}

	public int getTypeKind() {
		return fTypeKind;
	}

}
