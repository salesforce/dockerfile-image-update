package com.salesforce.dva.dockerfileimageupdate.subcommands;

import com.salesforce.dva.dockerfileimageupdate.utils.DockerfileGithubUtil;
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
