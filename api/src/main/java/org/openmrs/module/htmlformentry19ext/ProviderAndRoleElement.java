/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.htmlformentry19ext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.openmrs.EncounterRole;
import org.openmrs.Person;
import org.openmrs.Provider;
import org.openmrs.api.EncounterService;
import org.openmrs.api.context.Context;
import org.openmrs.module.htmlformentry.BadFormDesignException;
import org.openmrs.module.htmlformentry.FormEntryContext;
import org.openmrs.module.htmlformentry.FormEntryContext.Mode;
import org.openmrs.module.htmlformentry.FormEntrySession;
import org.openmrs.module.htmlformentry.FormSubmissionError;
import org.openmrs.module.htmlformentry.action.FormSubmissionControllerAction;
import org.openmrs.module.htmlformentry.element.HtmlGeneratorElement;
import org.openmrs.module.htmlformentry.widget.ErrorWidget;
import org.springframework.util.StringUtils;


/**
 *
 */
public class ProviderAndRoleElement implements HtmlGeneratorElement, FormSubmissionControllerAction {

	boolean required = false;
    int count = 1;

	EncounterRoleWidget roleWidget;
	ErrorWidget roleErrorWidget;
	List<ProviderWidget> providerWidgets = new ArrayList<ProviderWidget>();
	List<ErrorWidget> providerErrorWidgets = new ArrayList<ErrorWidget>();
	
	// in case EncounterRole is specified as a parameter to the tag
	EncounterRole encounterRole;
	
	/**
     * @param parameters
	 * @throws BadFormDesignException 
     */
    public ProviderAndRoleElement(FormEntryContext context, Map<String, String> parameters) throws BadFormDesignException {
    	if (parameters.containsKey("required")) {
    		required = Boolean.valueOf(parameters.get("required"));
        }
        if (parameters.containsKey("count")) {
            count = Integer.valueOf(parameters.get("count"));
        }

        // if there is an encounterRole specified, use that; otherwise create a roleWidget
    	if (parameters.containsKey("encounterRole")) {
    		EncounterService es = Context.getEncounterService();
    		String param = parameters.get("encounterRole");
    		try {
    			encounterRole = es.getEncounterRole(Integer.valueOf(param));
    		} catch (Exception ex) {
    			encounterRole = es.getEncounterRoleByUuid(param);
    		}
    		if (encounterRole == null)
    			throw new BadFormDesignException("Cannot find EncounterRole \"" + param + "\"");
    		
    	} else {
    		roleWidget = new EncounterRoleWidget();
    		roleErrorWidget = new ErrorWidget();
    		context.registerWidget(roleWidget);
    		context.registerErrorWidget(roleWidget, roleErrorWidget);
    	}

        boolean initialProviderSet = false;

        // handle the case where no encounterRole attribute is specified
        if (encounterRole == null) {
            if (count > 1) {
                throw new BadFormDesignException("HTML Form Entry does not (yet) support multiple providers per encounter without specifying encounter roles");
            }

            ProviderWidget providerWidget = new ProviderWidget();
            ErrorWidget providerErrorWidget = new ErrorWidget();
            context.registerWidget(providerWidget);
            context.registerErrorWidget(providerWidget, providerErrorWidget);
            providerWidgets.add(providerWidget);
            providerErrorWidgets.add(providerErrorWidget);

            if (context.getExistingEncounter() != null) {
                Map<EncounterRole, Set<Provider>> byRoles = context.getExistingEncounter().getProvidersByRoles();
                if (byRoles.size() > 0) {
                    // currently we only support a single provider in this mode
                    if (byRoles.size() > 1 || byRoles.values().iterator().next().size() > 1) {
                        throw new BadFormDesignException("HTML Form Entry does not (yet) support multiple providers per encounter without specifying encounter roles");
                    }

                    Entry<EncounterRole, Set<Provider>> roleAndProvider = byRoles.entrySet().iterator().next();
                    Provider p = roleAndProvider.getValue().iterator().next();
                    providerWidget.setInitialValue(p);
                    initialProviderSet = true;
                    roleWidget.setInitialValue(roleAndProvider.getKey());
                }
            }
        }
        // handle the case where an encounter role is specified
        else {

            // get any existing providers for the specified role
            List<Provider> byRole = null;
            if (context.getExistingEncounter() != null) {
                byRole = new ArrayList<Provider>(context.getExistingEncounter().getProvidersByRole(encounterRole));
            }

            // register the provider widgets, setting any existing provider values
    	    for (int currentIteration = 0; currentIteration < count; currentIteration++) {

                ProviderWidget providerWidget = new ProviderWidget();
                ErrorWidget providerErrorWidget = new ErrorWidget();
                context.registerWidget(providerWidget);
                context.registerErrorWidget(providerWidget, providerErrorWidget);
                providerWidgets.add(providerWidget);
                providerErrorWidgets.add(providerErrorWidget);

                if (byRole != null && byRole.size() > currentIteration) {
    				providerWidget.setInitialValue(byRole.get(currentIteration));
    				initialProviderSet = true;
    			}
            }
        }

        // set the default provider if no existing provider and a default is specified
    	if (!initialProviderSet && providerWidgets.iterator().next() != null && StringUtils.hasText(parameters.get("default"))) {
    		String temp = parameters.get("default");
    		Provider provider = null;
    		if ("currentUser".equals(temp)) {
    			Person me = Context.getAuthenticatedUser().getPerson();
    			Collection<Provider> candidates = Context.getProviderService().getProvidersByPerson(me);
    			if (candidates.size() > 0)
    				provider = candidates.iterator().next();
    		} else {
	    		try {
	    			provider = Context.getProviderService().getProvider(Integer.valueOf(temp));
	    		} catch (Exception ex) {
	    			provider = Context.getProviderService().getProviderByUuid(temp);
	    		}
    		}
    		if (provider != null) {
    			providerWidgets.iterator().next().setInitialValue(provider);
    		}
    	}
    }

	/**
     * @see org.openmrs.module.htmlformentry.element.HtmlGeneratorElement#generateHtml(org.openmrs.module.htmlformentry.FormEntryContext)
     */
    @Override
    public String generateHtml(FormEntryContext context) {
    	StringBuilder ret = new StringBuilder();
    	if (roleWidget != null) {
    		ret.append(roleWidget.generateHtml(context));
    		if (context.getMode() != Mode.VIEW)
    			ret.append(roleErrorWidget.generateHtml(context));
    		ret.append(": ");
    	}

        Iterator<ErrorWidget> errorWidgetIterator = providerErrorWidgets.iterator();
        for (ProviderWidget providerWidget : providerWidgets) {
			ret.append(providerWidget.generateHtml(context));
			if (context.getMode() != Mode.VIEW) {
				ret.append(errorWidgetIterator.next().generateHtml(context));
            }
		}

	    return ret.toString();
    }
    
    /**
     * @see org.openmrs.module.htmlformentry.action.FormSubmissionControllerAction#validateSubmission(org.openmrs.module.htmlformentry.FormEntryContext, javax.servlet.http.HttpServletRequest)
     */
    @Override
    public Collection<FormSubmissionError> validateSubmission(FormEntryContext context, HttpServletRequest submission) {

        if (!required) {
    		return null;
        }

        EncounterRole role = encounterRole;
    	Provider provider = null;
    	List<FormSubmissionError> ret = new ArrayList<FormSubmissionError>();
    	if (roleWidget != null) {
    		role = (EncounterRole) roleWidget.getValue(context, submission);
    		if (role == null)
        		ret.add(new FormSubmissionError(roleWidget, Context.getMessageSourceService().getMessage("htmlformentry.error.required")));
    	}

        boolean atLeastOneProviderSpecified = false;

        for (ProviderWidget providerWidget : providerWidgets) {
            if (providerWidget != null) {
                provider = (Provider) providerWidget.getValue(context, submission);
                if (provider != null) {
                    atLeastOneProviderSpecified = true;
                }
            }
        }

    	if (!atLeastOneProviderSpecified) {
            ret.add(new FormSubmissionError(providerWidgets.get(0), Context.getMessageSourceService().getMessage("htmlformentry.error.required")));
    	}
    	return ret;
    }

	/**
     * @see org.openmrs.module.htmlformentry.action.FormSubmissionControllerAction#handleSubmission(org.openmrs.module.htmlformentry.FormEntrySession, javax.servlet.http.HttpServletRequest)
     */
    @Override
    public void handleSubmission(FormEntrySession session, HttpServletRequest submission) {

        EncounterRole role = encounterRole;
    	Provider provider = null;

        if (roleWidget != null) {
    		role = (EncounterRole) roleWidget.getValue(session.getContext(), submission);
    	}

        Set<Provider> currentProvidersForRole = session.getSubmissionActions().getCurrentEncounter().getProvidersByRole(role);

        for (ProviderWidget providerWidget : providerWidgets) {
    		provider = (Provider) providerWidget.getValue(session.getContext(), submission);

            if (provider != null) {
                // if this provider it not currently one of the provider, add her
                if (currentProvidersForRole != null || !currentProvidersForRole.contains(provider)) {
                    session.getSubmissionActions().getCurrentEncounter().addProvider(role, provider);
                }
                // we remove this provider so that we end up with a list of providers to void
                currentProvidersForRole.remove(provider);
            }
        }

        // now remove any current providers that we haven't found in the provider list
        for (Provider providerToRemove : currentProvidersForRole) {
            session.getSubmissionActions().getCurrentEncounter().removeProvider(role, providerToRemove);
        }
    }
}
