package com.coremedia.caas.service.repository;

import com.coremedia.caas.service.repository.content.ContentProxy;
import com.coremedia.caas.service.repository.content.ContentProxyImpl;
import com.coremedia.cap.content.Content;
import com.coremedia.cap.content.ContentRepository;
import com.coremedia.cap.struct.Struct;

import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Maps;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import javax.validation.constraints.NotNull;

public class ProxyFactoryImpl implements ProxyFactory {

  private ContentRepository contentRepository;
  private RootContext rootContext;


  public ProxyFactoryImpl(ContentRepository contentRepository, RootContext rootContext) {
    this.contentRepository = contentRepository;
    this.rootContext = rootContext;
  }


  @Override
  public RootContext getRootContext() {
    return rootContext;
  }


  @Override
  public Object makeRoot(Object source) {
    if (source instanceof Content) {
      return makeContentProxy((Content) source);
    }
    else {
      throw new IllegalArgumentException("Invalid root source " + source.getClass().getName());
    }
  }


  @Override
  public Object makeProxy(Object source) {
    if (source == null) {
      return null;
    }
    if (source instanceof Content) {
      return makeContentProxy((Content) source);
    }
    if (source instanceof Struct) {
      return makeStruct((Struct) source);
    }
    if (source instanceof SortedMap) {
      return makeSortedMap((SortedMap<?, ?>) source);
    }
    if (source instanceof Map) {
      return makeMap((Map<?, ?>) source);
    }
    if (source instanceof Collection) {
      return makeList((Collection) source);
    }
    return source;
  }


  @Override
  public ContentProxy makeContentProxy(@NotNull Content source) {
    if (rootContext.getAccessControl().check(source)) {
      return new ContentProxyImpl(source, this);
    }
    return null;
  }

  @Override
  public ContentProxy makeContentProxyFromId(@NotNull String id) {
    Content content = contentRepository.getContent(id);
    if (content != null) {
      return makeContentProxy(content);
    }
    return null;
  }

  @Override
  public List<ContentProxy> makeContentProxyList(@NotNull Collection<Content> source) {
    return FluentIterable.from(source).transform(this::makeContentProxy).filter(Predicates.notNull()).toList();
  }

  @Override
  public List<ContentProxy> makeContentProxyList(@NotNull Collection<Content> source, int limit) {
    return FluentIterable.from(source).transform(this::makeContentProxy).filter(Predicates.notNull()).limit(limit).toList();
  }

  @Override
  public List<ContentProxy> makeContentProxyListFromIds(@NotNull Collection<String> ids) {
    return FluentIterable.from(ids).transform(this::makeContentProxyFromId).filter(Predicates.notNull()).toList();
  }

  @Override
  public List<ContentProxy> makeContentProxyListFromIds(@NotNull Collection<String> ids, int limit) {
    return FluentIterable.from(ids).transform(this::makeContentProxyFromId).filter(Predicates.notNull()).limit(limit).toList();
  }


  private List<Object> makeList(Collection<?> source) {
    return FluentIterable.from(source).transform(this::makeProxy).filter(Predicates.notNull()).toList();
  }

  private Map<?, ?> makeMap(Map<?, ?> source) {
    return Maps.transformValues(source, this::makeProxy);
  }

  private SortedMap<?, ?> makeSortedMap(SortedMap<?, ?> source) {
    return Maps.transformValues(source, this::makeProxy);
  }

  private Map<String, Object> makeStruct(Struct source) {
    if (source != null) {
      return Maps.transformValues(source.toNestedMaps(), this::makeProxy);
    }
    return Collections.emptyMap();
  }
}
