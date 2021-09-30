# Copyright (c) Microsoft Corporation. All rights reserved.
# Licensed under the MIT License.

import flatbuffers


class InferenceSession(object):
    __slots__ = ['_tab']

    @classmethod
    def GetRootAsInferenceSession(cls, buf, offset):
        n = flatbuffers.encode.Get(flatbuffers.packer.uoffset, buf, offset)
        x = InferenceSession()
        x.Init(buf, n + offset)
        return x

    # InferenceSession
    def Init(self, buf, pos):
        self._tab = flatbuffers.table.Table(buf, pos)

    # InferenceSession
    def Model(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(6))
        if o != 0:
            x = self._tab.Indirect(o + self._tab.Pos)
            obj = Model()
            obj.Init(self._tab.Bytes, x)
            return obj
        return None


class Model(object):
    __slots__ = ['_tab']

    # Model
    def Init(self, buf, pos):
        self._tab = flatbuffers.table.Table(buf, pos)


    # Model
    def Graph(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(18))
        if o != 0:
            x = self._tab.Indirect(o + self._tab.Pos)
            obj = Graph()
            obj.Init(self._tab.Bytes, x)
            return obj
        return None


class Graph(object):
    __slots__ = ['_tab']

    # Graph
    def Init(self, buf, pos):
        self._tab = flatbuffers.table.Table(buf, pos)

    # Graph
    def Nodes(self, j):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(8))
        if o != 0:
            x = self._tab.Vector(o)
            x += flatbuffers.number_types.UOffsetTFlags.py_type(j) * 4
            x = self._tab.Indirect(x)
            obj = Node()
            obj.Init(self._tab.Bytes, x)
            return obj
        return None

    # Graph
    def NodesLength(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(8))
        if o != 0:
            return self._tab.VectorLen(o)
        return 0


class Node(object):
    __slots__ = ['_tab']

    # Node
    def Init(self, buf, pos):
        self._tab = flatbuffers.table.Table(buf, pos)

    # Node
    def Domain(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(8))
        if o != 0:
            return self._tab.String(o + self._tab.Pos)
        return None

    # Node
    def OpType(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(14))
        if o != 0:
            return self._tab.String(o + self._tab.Pos)
        return None

