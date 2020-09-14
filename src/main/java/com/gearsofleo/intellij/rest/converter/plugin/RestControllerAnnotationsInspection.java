package com.gearsofleo.intellij.rest.converter.plugin;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RestControllerAnnotationsInspection extends BaseInspection {

	@NotNull
	@Override
	protected String buildErrorString(Object... infos) {
		return "#ref can be a Spring MVC REST controller";
	}

	@Override
	public boolean shouldInspect(PsiFile file) {
		if (JavaPsiFacade.getInstance(file.getProject())
				.findClass("org.springframework.web.bind.annotation.RestController", file.getResolveScope()) == null) {
			return false;
		}
		return super.shouldInspect(file);
	}

	@Nullable
	@Override
	protected InspectionGadgetsFix buildFix(Object... infos) {
		return new JaxRsToSpringMvcMigrationFix();
	}


	@Override
	public BaseInspectionVisitor buildVisitor() {
		return new BaseInspectionVisitor() {

			@Override
			public void visitClass(PsiClass aClass) {
				if (aClass.hasAnnotation(JaxRsClasses.PATH)) {
					registerClassError(aClass);
				}
			}
		};
	}
}
