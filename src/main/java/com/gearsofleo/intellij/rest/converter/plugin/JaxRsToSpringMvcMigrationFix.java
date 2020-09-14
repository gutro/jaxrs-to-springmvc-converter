package com.gearsofleo.intellij.rest.converter.plugin;

import java.util.Collection;
import java.util.Optional;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiConstantEvaluationHelper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class JaxRsToSpringMvcMigrationFix extends InspectionGadgetsFix {

	@Override
	protected void doFix(Project project, ProblemDescriptor descriptor) {
		JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
		PsiConstantEvaluationHelper evaluationHelper = javaPsiFacade.getConstantEvaluationHelper();
		PsiElementFactory psiElementFactory = PsiElementFactory.getInstance(project);
		JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);

		PsiClass psiClass = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiClass.class);

		if (psiClass != null) {
			processClassAnnotations(psiClass, javaCodeStyleManager, psiElementFactory, evaluationHelper);

			for (PsiMethod psiMethod : psiClass.getMethods()) {
				processMethodAnnotations(psiMethod, javaCodeStyleManager);
				processMethodArguments(psiMethod, javaCodeStyleManager);
				processMethodReturnTypeAndBody(psiMethod, javaCodeStyleManager, psiElementFactory, javaPsiFacade);
			}
		}
	}

	private void processClassAnnotations(PsiClass psiClass, JavaCodeStyleManager javaCodeStyleManager, PsiElementFactory psiElementFactory, PsiConstantEvaluationHelper evaluationHelper) {
		PsiAnnotation pathAnnotation = psiClass.getAnnotation(JaxRsClasses.PATH);
		if (pathAnnotation != null && psiClass.getModifierList() != null) {
			PsiAnnotationMemberValue pathValueExpression = pathAnnotation.findAttributeValue("value");
			Object pathValue = evaluationHelper.computeConstantExpression(pathValueExpression);
			pathAnnotation.delete();

			Optional.ofNullable(psiClass.getAnnotation(SpringMvcClasses.SERVICE)).ifPresent(PsiElement::delete);

			PsiLiteralExpression updatedPathValue = (PsiLiteralExpression) psiElementFactory
					.createExpressionFromText("\"api/" + pathValue + "\"", psiClass);

			PsiAnnotation requestMappingAnnotation = psiClass.getModifierList()
					.addAnnotation(SpringMvcClasses.REQUEST_MAPPING);
			requestMappingAnnotation.setDeclaredAttributeValue("value", updatedPathValue);
			javaCodeStyleManager.shortenClassReferences(requestMappingAnnotation);
			PsiAnnotation restControllerAnnotation = psiClass.getModifierList()
					.addAnnotation(SpringMvcClasses.REST_CONTROLLER);
			javaCodeStyleManager.shortenClassReferences(restControllerAnnotation);
		}
	}

	private void processMethodReturnTypeAndBody(PsiMethod psiMethod, JavaCodeStyleManager javaCodeStyleManager,
			PsiElementFactory psiElementFactory, JavaPsiFacade javaPsiFacade) {

		PsiType returnType = psiMethod.getReturnType();
		if (returnType != null && returnType.equalsToText(JaxRsClasses.RESPONSE)) {

			PsiType genericType = null;
			Collection<PsiReturnStatement> returnStatements = PsiTreeUtil
					.findChildrenOfType(psiMethod.getBody(), PsiReturnStatement.class);
			for (PsiReturnStatement returnStatement : returnStatements) {
				if (returnStatement.getReturnValue() != null
						&& returnStatement.getReturnValue().getType() != null
						&& returnStatement.getReturnValue().getType().equalsToText(JaxRsClasses.RESPONSE)) {
					ResponseMethodChainMigration responseMethodChainMigration =
							new ResponseMethodChainMigration(
									psiElementFactory,
									javaPsiFacade,
									psiMethod.getResolveScope());
					genericType = responseMethodChainMigration
							.findGenericResponseType(returnStatement.getReturnValue());
					responseMethodChainMigration.migrate(returnStatement.getReturnValue());
				}
			}

			PsiTypeElement springReturnType = psiElementFactory
					.createTypeElementFromText(SpringMvcClasses.RESPONSE_ENTITY, psiMethod);
			if (genericType != null) {
				springReturnType = psiElementFactory
						.createTypeElementFromText(SpringMvcClasses.RESPONSE_ENTITY
								+ "<" + genericType.getCanonicalText() + ">", psiMethod);
			}
			javaCodeStyleManager.shortenClassReferences(springReturnType);
			psiMethod.getReturnTypeElement().replace(springReturnType);
		}
	}

	private void processMethodArguments(PsiMethod psiMethod, JavaCodeStyleManager javaCodeStyleManager) {
		for (PsiParameter parameter : psiMethod.getParameterList().getParameters()) {
			if (parameter.getModifierList() != null) {
				PsiAnnotation pathParamAnnotation = parameter.getModifierList().findAnnotation(JaxRsClasses.PATH_PARAM);
				PsiAnnotation headerParamAnnotation = parameter.getModifierList()
						.findAnnotation(JaxRsClasses.HEADER_PARAM);
				if (pathParamAnnotation != null) {
					PsiAnnotationMemberValue pathValue = pathParamAnnotation.findAttributeValue("value");
					PsiAnnotation pathVariableAnnotation = parameter.getModifierList()
							.addAnnotation(SpringMvcClasses.PATH_VARIABLE);
					pathVariableAnnotation.setDeclaredAttributeValue("value", pathValue);
					javaCodeStyleManager.shortenClassReferences(pathVariableAnnotation);
					pathParamAnnotation.delete();
				}
				else if (headerParamAnnotation != null) {
					PsiAnnotationMemberValue pathValue = headerParamAnnotation.findAttributeValue("value");
					PsiAnnotation pathVariableAnnotation = parameter.getModifierList()
							.addAnnotation(SpringMvcClasses.REQUEST_HEADER);
					pathVariableAnnotation.setDeclaredAttributeValue("value", pathValue);
					javaCodeStyleManager.shortenClassReferences(pathVariableAnnotation);
					headerParamAnnotation.delete();
				}
				else {
					PsiAnnotation requestBodyAnnotation = parameter.getModifierList()
							.addAnnotation(SpringMvcClasses.REQUEST_BODY);
					javaCodeStyleManager.shortenClassReferences(requestBodyAnnotation);
				}
			}
		}
	}

	private void processMethodAnnotations(PsiMethod psiMethod, JavaCodeStyleManager javaCodeStyleManager) {
		PsiAnnotation pathAnnotation = psiMethod.getAnnotation(JaxRsClasses.PATH);

		if (pathAnnotation != null) {
			replaceVerbAnnotation(psiMethod, psiMethod
					.getAnnotation(JaxRsClasses.DELETE), pathAnnotation, SpringMvcClasses.DELETE_MAPPING, javaCodeStyleManager);
			replaceVerbAnnotation(psiMethod, psiMethod
					.getAnnotation(JaxRsClasses.GET), pathAnnotation, SpringMvcClasses.GET_MAPPING, javaCodeStyleManager);
			replaceVerbAnnotation(psiMethod, psiMethod
					.getAnnotation(JaxRsClasses.PATCH), pathAnnotation, SpringMvcClasses.PATCH_MAPPING, javaCodeStyleManager);
			replaceVerbAnnotation(psiMethod, psiMethod
					.getAnnotation(JaxRsClasses.POST), pathAnnotation, SpringMvcClasses.POST_MAPPING, javaCodeStyleManager);
			replaceVerbAnnotation(psiMethod, psiMethod
					.getAnnotation(JaxRsClasses.PUT), pathAnnotation, SpringMvcClasses.PUT_MAPPING, javaCodeStyleManager);

			//HEAD and OPTIONS currently not supported (there are no corresponding meta-annotated annotations in Spring MVC)
		}
	}

	private void replaceVerbAnnotation(PsiMethod psiMethod,
			PsiAnnotation httpVerbAnnotation,
			PsiAnnotation pathAnnotation,
			String springMappingAnnotationFqn,
			JavaCodeStyleManager javaCodeStyleManager) {
		if (httpVerbAnnotation != null) {
			PsiAnnotationMemberValue pathValue = pathAnnotation.findAttributeValue("value");

			httpVerbAnnotation.delete();
			pathAnnotation.delete();
			PsiAnnotation springMappingAnnotation = psiMethod.getModifierList()
					.addAnnotation(springMappingAnnotationFqn);
			springMappingAnnotation.setDeclaredAttributeValue("value", pathValue);
			javaCodeStyleManager.shortenClassReferences(springMappingAnnotation);
		}
	}

	@Nls
	@NotNull
	@Override
	public String getName() {
		return "Convert JAX-RS controller to Spring MVC";
	}

	@NotNull
	@Override
	public String getFamilyName() {
		return "Spring REST";
	}
}
