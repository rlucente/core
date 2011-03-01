/* 
 * JBoss, Home of Professional Open Source 
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved. 
 * See the copyright.txt in the distribution for a 
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use, 
 * modify, copy, or redistribute it subject to the terms and conditions 
 * of the GNU Lesser General Public License, v. 2.1. 
 * This program is distributed in the hope that it will be useful, but WITHOUT A 
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details. 
 * You should have received a copy of the GNU Lesser General Public License, 
 * v.2.1 along with this distribution; if not, write to the Free Software 
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, 
 * MA  02110-1301, USA.
 */

package org.switchyard;

import org.switchyard.metadata.ExchangeContract;
import org.switchyard.metadata.ServiceInterface;

import javax.xml.namespace.QName;

/**
 * A service registered with the SwitchYard runtime.
 */
public interface ServiceReference {
    /**
     * Qualified name of the service.
     * @return service name
     */
    QName getName();
    /**
    * Interface metadata for the registered service.
    * @return the service interface
    */
    ServiceInterface getInterface();
    /**
     * Creates a new Exchange to invoke this service with the specified exchange
     * pattern.
     * @param contract the exchange contract to use
     * @return a new Exchange instance
     */
    Exchange createExchange(ExchangeContract contract);
    /**
     * Creates a new Exchange to invoke this service with the specified exchange
     * pattern.  The supplied ExchangeHandler is used to handle any faults or
     * reply messages that are generated as part of the message exchange.
     * @param contract the exchange contract to use
     * @param handler used to process response and fault messages
     * @return a new Exchange instance
     */
    Exchange createExchange(ExchangeContract contract, ExchangeHandler handler);
}