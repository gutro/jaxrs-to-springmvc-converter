package com.gearsofleo.intellij.rest.converter.plugin;

import com.intellij.lang.jvm.JvmModifier;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;

public class ResponseMethodChainMigration {
	private final PsiElementFactory psiElementFactory;
	private final JavaPsiFacade javaPsiFacade;
	private final GlobalSearchScope globalSearchScope;

	ResponseMethodChainMigration(PsiElementFactory psiElementFactory,
			JavaPsiFacade javaPsiFacade,
			GlobalSearchScope globalSearchScope) {
		this.psiElementFactory = psiElementFactory;
		this.javaPsiFacade = javaPsiFacade;
		this.globalSearchScope = globalSearchScope;
	}

	public PsiType findGenericResponseType(PsiExpression expression) {
		PsiMethodCallExpression methodCallExpression = PsiTreeUtil
				.findChildOfType(expression, PsiMethodCallExpression.class);
		if (methodCallExpression != null) {
			PsiMethod calledMethod = methodCallExpression.resolveMethod();
			PsiClass methodCalledOnType = calledMethod.getContainingClass();
			String methodName = methodCallExpression.getMethodExpression().getReferenceName();

			if (methodCalledOnType != null
					&& methodName != null
					&& !methodCallExpression.getArgumentList().isEmpty()) {
				if (methodCalledOnType.getQualifiedName().equals(JaxRsClasses.RESPONSE)
						&& (methodName.equals("ok") || methodName.equals("accepted"))) {
					return methodCallExpression.getArgumentList().getExpressions()[0].getType();
				}
				else if (methodCalledOnType.getQualifiedName().equals(JaxRsClasses.RESPONSE_BUILDER)
						&& methodName.equals("entity")) {
					return methodCallExpression.getArgumentList().getExpressions()[0].getType();
				}
			}
		}
		return null;
	}

	public PsiElement migrate(PsiExpression expression) {
		if (expression instanceof PsiMethodCallExpression) {
			return migrate((PsiMethodCallExpression) expression);
		}
		return expression;
	}

	/**
	 * Migrate Response creation to ResponseEntity creation
	 * E.g. FROM:
	 * Response.ok().entity(document).build();
	 * TO:
	 * ResponseEntity.ok().body(document);
	 *
	 * Or FROM:
	 * Response.ok(document).build();
	 * TO:
	 * ResponseEntity.ok(document);
	 */
	public PsiElement migrate(PsiMethodCallExpression methodCallExpression) {

		System.out.println("---- PROCESSING METHOD CALL: " + methodCallExpression);

		PsiMethod calledMethod = methodCallExpression.resolveMethod();
		if (calledMethod != null) {

			System.out.println("method call containing class; " + calledMethod.getContainingClass());

			PsiClass methodCalledOnType = calledMethod.getContainingClass();
			String methodName = methodCallExpression.getMethodExpression().getReferenceName();

			if (methodCalledOnType != null
					&& JaxRsClasses.RESPONSE.equals(methodCalledOnType.getQualifiedName())
					&& calledMethod.hasModifier(JvmModifier.STATIC)) {
				System.out.println("----------- REPLACING STATIC Response CALL");
				PsiClass responseEntityClass = javaPsiFacade
						.findClass(SpringMvcClasses.RESPONSE_ENTITY, globalSearchScope);

				methodCallExpression.getMethodExpression()
						.setQualifierExpression(psiElementFactory
								.createReferenceExpression(responseEntityClass));
				return methodCallExpression;
			}
			if (methodCalledOnType != null
					&& "build".equals(methodName)
					&& JaxRsClasses.RESPONSE_BUILDER.equals(methodCalledOnType.getQualifiedName())) {
				System.out.println("----------- REMOVING #build() CALL");
				return methodCallExpression.replace(migrate(methodCallExpression.getMethodExpression()
						.getQualifierExpression()));
			}
			if (methodCalledOnType != null
					&& "entity".equals(methodName)
					&& JaxRsClasses.RESPONSE_BUILDER.equals(methodCalledOnType.getQualifiedName())) {
				System.out.println("----------- REPLACING #entity() CALL WILL #body CALL");
				migrate(methodCallExpression.getMethodExpression()).getReference().handleElementRename("body");
				PsiElement migratedQualifier = migrate(methodCallExpression.getMethodExpression()
						.getQualifierExpression());
				methodCallExpression.getMethodExpression().getQualifierExpression().replace(migratedQualifier);
				return methodCallExpression;
			}
		}

		PsiExpression qualifier = methodCallExpression.getMethodExpression().getQualifierExpression();
		if (qualifier != null) {
			System.out.println("----------- MIGRATING QUALIFIER RECURSIVELY");
			qualifier.replace(migrate(qualifier));
			return methodCallExpression;
		}
		System.out.println("----------- NO MIGRATION");
		return methodCallExpression;
	}
}
