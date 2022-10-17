package org.asf.rats;

import java.io.IOException;

/**
 * 
 * Authentication provider interface, by our standard, assign/use the
 * 'connective.standard.authprovider' memory entry to assign/access the
 * implementation native to your connective server software.
 * 
 * @author Stefan0436 - AerialWorks Software Foundation
 *
 */
public interface IAuthenticationProvider {
	public boolean authenticate(String group, String username, char[] password) throws IOException;
}
