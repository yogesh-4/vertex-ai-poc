package org.example.git;


import org.eclipse.egit.github.core.IRepositoryIdProvider;
import org.eclipse.egit.github.core.Reference;
import org.eclipse.egit.github.core.TypedResource;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.DataService;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.eclipse.egit.github.core.client.IGitHubConstants.*;

public class DataServiceExtend extends DataService {

    public DataServiceExtend(GitHubClient gitHubClient){
        super(gitHubClient);
    }

    /**
     * Delete reference
     *
     * @param repository
     * @param reference
     * @return created reference
     * @throws IOException
     */
    public void deleteReference(IRepositoryIdProvider repository,
                                Reference reference) throws IOException {
        final String id = getId(repository);
        if (reference == null)
            throw new IllegalArgumentException("Reference cannot be null"); //$NON-NLS-1$
        TypedResource object = reference.getObject();
        if (object == null)
            throw new IllegalArgumentException("Object cannot be null"); //$NON-NLS-1$
        String ref = reference.getRef();
        if (ref == null)
            throw new IllegalArgumentException("Ref cannot be null"); //$NON-NLS-1$
        if (ref.length() == 0)
            throw new IllegalArgumentException("Ref cannot be empty"); //$NON-NLS-1$

        StringBuilder uri = new StringBuilder();
        uri.append(SEGMENT_REPOS);
        uri.append('/').append(id);
        uri.append(SEGMENT_GIT);
        if (!ref.startsWith("refs/")) //$NON-NLS-1$
            uri.append(SEGMENT_REFS);
        uri.append('/').append(ref);
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("sha", object.getSha()); //$NON-NLS-1$
        params.put("ref", reference.getRef());
        client.delete(uri.toString(), params);
    }
}

