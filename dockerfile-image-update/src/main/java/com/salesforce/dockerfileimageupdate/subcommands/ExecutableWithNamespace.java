/*
 * Copyright (c) 2017, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.subcommands;

import com.salesforce.dockerfileimageupdate.utils.DockerfileGithubUtil;
import net.sourceforge.argparse4j.inf.Namespace;

import java.io.IOException;

/**
 * 
 * @author minho-park
 *
 */
public interface ExecutableWithNamespace {

    void execute(Namespace ns, DockerfileGithubUtil dockerfileGithubUtil)
            throws IOException, IllegalAccessException, InstantiationException, InterruptedException;

}
