/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.subcommands;

import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import net.sourceforge.argparse4j.inf.Namespace;

import java.io.IOException;

/**
 * 
 * @author minho-park
 *
 */
public interface ExecutableWithNamespace {

    void execute(Namespace ns, DockerfileGitHubUtil dockerfileGitHubUtil)
            throws IOException, IllegalAccessException, InstantiationException, InterruptedException;

}
