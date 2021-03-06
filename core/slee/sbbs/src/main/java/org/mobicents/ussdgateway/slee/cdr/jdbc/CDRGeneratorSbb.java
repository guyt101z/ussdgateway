/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.mobicents.ussdgateway.slee.cdr.jdbc;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.slee.ActivityContextInterface;
import javax.slee.CreateException;
import javax.slee.RolledBackContext;
import javax.slee.SbbContext;

import org.mobicents.slee.resource.jdbc.JdbcActivity;
import org.mobicents.slee.resource.jdbc.JdbcActivityContextInterfaceFactory;
import org.mobicents.slee.resource.jdbc.JdbcResourceAdaptorSbbInterface;
import org.mobicents.slee.resource.jdbc.event.JdbcTaskExecutionThrowableEvent;
import org.mobicents.slee.resource.jdbc.task.simple.SimpleJdbcTaskResultEvent;
import org.mobicents.ussdgateway.slee.USSDBaseSbb;
import org.mobicents.ussdgateway.slee.cdr.ChargeInterface;
import org.mobicents.ussdgateway.slee.cdr.ChargeInterfaceParent;
import org.mobicents.ussdgateway.slee.cdr.RecordStatus;
import org.mobicents.ussdgateway.slee.cdr.USSDCDRState;
import org.mobicents.ussdgateway.slee.cdr.jdbc.task.CDRCreateTask;
import org.mobicents.ussdgateway.slee.cdr.jdbc.task.CDRTableCreateTask;
import org.mobicents.ussdgateway.slee.cdr.jdbc.task.CDRTaskBase;

/**
 * @author baranowb
 * 
 */
public abstract class CDRGeneratorSbb extends USSDBaseSbb implements ChargeInterface {
	private static final String CDR_GENERATED_TO = "Database";
	
    public CDRGeneratorSbb() {
		super("CDRGeneratorSbb");
	}

    // --------------- ChargeInterface methods ---------------

    /*
     * (non-Javadoc)
     * 
     * @see org.mobicents.ussdgateway.slee.cdr.ChargeInterface#init(boolean)
     */
    @Override
    public void init(final boolean reset) {
        if(this.logger.isFineEnabled()){
            this.logger.fine("Generating table");
        }
        
//        UssdPropertiesManagement ussdPropertiesManagement = UssdPropertiesManagement.getInstance();
//        ussdPropertiesManagement.setCdrLoggedTo(CDR_GENERATED_TO);
        
        CDRTableCreateTask task = new CDRTableCreateTask(this.logger, reset);
        executeTask(task);
    }


	/* (non-Javadoc)
     * @see org.mobicents.ussdgateway.slee.cdr.ChargeInterface#createRecord(org.mobicents.ussdgateway.slee.cdr.Status)
     */
    @Override
    public void createRecord(RecordStatus outcome) {
        USSDCDRState state = getState();
        if(state.isGenerated()){
            this.logger.severe("");
        }else{
            if(this.logger.isFineEnabled()){
                this.logger.fine("Generating record, status '"+outcome+"' for '"+state+"'");
            }
            state.setRecordStatus(outcome);
            state.setGenerated(true);
            this.setState(state);
            CDRCreateTask task = new CDRCreateTask(this.logger, getState());
            executeTask(task);
        }
    }


    /*
     * (non-Javadoc)
     * 
     * @see org.mobicents.ussdgateway.slee.cdr.ChargeInterface#setState(org.mobicents.ussdgateway.slee.cdr.USSDCDRState)
     */
    @Override
    public void setState(USSDCDRState state) {
        this.setUSSDCDRState(state);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.mobicents.ussdgateway.slee.cdr.ChargeInterface#getState()
     */
    @Override
    public USSDCDRState getState() {
        return this.getUSSDCDRState();
    }

    // --------------- JDBC event handlers ---------------
    /**
     * Simple method to create JDBC activity and execute given task.
     * 
     * @param queryJDBCTask
     */
    private void executeTask(CDRTaskBase jdbcTask) {
        JdbcActivity jdbcActivity = jdbcRA.createActivity();
        ActivityContextInterface jdbcACI = jdbcACIF.getActivityContextInterface(jdbcActivity);
        jdbcACI.attach(super.sbbContext.getSbbLocalObject());
        jdbcActivity.execute(jdbcTask);
    }

    // CMPs

    public abstract USSDCDRState getUSSDCDRState();

    public abstract void setUSSDCDRState(USSDCDRState state);

    // events

    /**
     * Event handler for {@link JdbcTaskExecutionThrowableEvent}.
     * 
     * @param event
     * @param aci
     */
    public void onJdbcTaskExecutionThrowableEvent(JdbcTaskExecutionThrowableEvent event, ActivityContextInterface aci) {
        if (super.logger.isWarningEnabled()) {
            super.logger.warning("Received a JdbcTaskExecutionThrowableEvent, as result of executed task " + event.getTask(),
                    event.getThrowable());
        }

        // end jdbc activity
        final JdbcActivity activity = (JdbcActivity) aci.getActivity();
        activity.endActivity();
        // call back parent
        final ChargeInterfaceParent parent = (ChargeInterfaceParent) super.sbbContext.getSbbLocalObject().getParent();
        final CDRTaskBase jdbcTask = (CDRTaskBase) event.getTask();
        jdbcTask.callParentOnFailure(parent, null, event.getThrowable());

    }

    public void onSimpleJdbcTaskResultEvent(SimpleJdbcTaskResultEvent event, ActivityContextInterface aci) {

        if (super.logger.isFineEnabled()) {
            super.logger.fine("Received a SimpleJdbcTaskResultEvent, as result of executed task " + event.getTask());
        }

        // end jdbc activity
        final JdbcActivity activity = (JdbcActivity) aci.getActivity();
        activity.endActivity();
        // call back parent
        final ChargeInterfaceParent parent = (ChargeInterfaceParent) super.sbbContext.getSbbLocalObject().getParent();
        final CDRTaskBase jdbcTask = (CDRTaskBase) event.getTask();
        jdbcTask.callParentOnSuccess(parent);

    }

    public void onStartServiceEvent(javax.slee.serviceactivity.ServiceStartedEvent event, ActivityContextInterface aci) {
        try {
            InitialContext ctx = new InitialContext();
            Boolean val = (Boolean) ctx.lookup("java:comp/env/reset");
            this.init(val);
        } catch (NamingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    // --------------- SBB callbacks ---------------

    /*
     * (non-Javadoc)
     * 
     * @see javax.slee.Sbb#sbbActivate()
     */
    @Override
    public void sbbActivate() {
        // TODO Auto-generated method stub

    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.slee.Sbb#sbbCreate()
     */
    @Override
    public void sbbCreate() throws CreateException {
        this.setUSSDCDRState(new USSDCDRState());

    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.slee.Sbb#sbbExceptionThrown(java.lang.Exception, java.lang.Object, javax.slee.ActivityContextInterface)
     */
    @Override
    public void sbbExceptionThrown(Exception arg0, Object arg1, ActivityContextInterface arg2) {
        // TODO Auto-generated method stub

    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.slee.Sbb#sbbLoad()
     */
    @Override
    public void sbbLoad() {
        // TODO Auto-generated method stub

    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.slee.Sbb#sbbPassivate()
     */
    @Override
    public void sbbPassivate() {
        // TODO Auto-generated method stub

    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.slee.Sbb#sbbPostCreate()
     */
    @Override
    public void sbbPostCreate() throws CreateException {
        // TODO Auto-generated method stub

    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.slee.Sbb#sbbRemove()
     */
    @Override
    public void sbbRemove() {
        // TODO Auto-generated method stub

    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.slee.Sbb#sbbRolledBack(javax.slee.RolledBackContext)
     */
    @Override
    public void sbbRolledBack(RolledBackContext arg0) {
        // TODO Auto-generated method stub

    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.slee.Sbb#sbbStore()
     */
    @Override
    public void sbbStore() {
        // TODO Auto-generated method stub

    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.slee.Sbb#setSbbContext(javax.slee.SbbContext)
     */
    @Override
    public void setSbbContext(SbbContext ctx) {
        super.setSbbContext(ctx);
        super.logger = ctx.getTracer(TRACER_NAME);
        super.jdbcRA = (JdbcResourceAdaptorSbbInterface) super.sbbContext.getResourceAdaptorInterface(
                JDBC_RESOURCE_ADAPTOR_ID, JDBC_RA_LINK);
        super.jdbcACIF = (JdbcActivityContextInterfaceFactory) super.sbbContext
                .getActivityContextInterfaceFactory(JDBC_RESOURCE_ADAPTOR_ID);
    }


    // /*
    // * (non-Javadoc)
    // *
    // * @see org.mobicents.slee.SbbExt#sbbConfigurationUpdate(org.mobicents.slee.ConfigProperties)
    // */
    // @Override
    // public void sbbConfigurationUpdate(ConfigProperties arg0) {
    // // TODO Auto-generated method stub
    //
    // }
    //
    // /*
    // * (non-Javadoc)
    // *
    // * @see org.mobicents.slee.SbbExt#sbbConfigure(org.mobicents.slee.ConfigProperties)
    // */
    // @Override
    // public void sbbConfigure(ConfigProperties arg0) {
    // // TODO Auto-generated method stub
    //
    // }
    //
    // /*
    // * (non-Javadoc)
    // *
    // * @see org.mobicents.slee.SbbExt#sbbUnconfigure()
    // */
    // @Override
    // public void sbbUnconfigure() {
    // // TODO Auto-generated method stub
    //
    // }
    //
    // /*
    // * (non-Javadoc)
    // *
    // * @see org.mobicents.slee.SbbExt#sbbVerifyConfiguration(org.mobicents.slee.ConfigProperties)
    // */
    // @Override
    // public void sbbVerifyConfiguration(ConfigProperties arg0) throws InvalidConfigurationException {
    // // TODO Auto-generated method stub
    //
    // }

}
