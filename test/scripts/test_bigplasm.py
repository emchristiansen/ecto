#!/usr/bin/env python
import ecto
import ecto_test

def test_plasm():
    plasm = ecto.Plasm()

    gen = ecto_test.Generate(step=1.0, start=0.0)
    incl, incr = ecto_test.Increment(), ecto_test.Increment()

    plasm.connect(gen, "out", incl, "in")
    plasm.connect(gen, "out", incr, "in")

    for j in []: # range(2): # one set of incs has already been added
        print j
        inc_nextl, inc_nextr = ecto_test.Increment(), ecto_test.Increment()
        plasm.connect(incl, "out", inc_nextl, "in")
        plasm.connect(incr, "out", inc_nextr, "in")
        incl, incr = inc_nextl, inc_nextr

    add = ecto_test.Add()
    plasm.connect(incl, "out", add, "left")
    plasm.connect(incr, "out", add, "right")
    printer = ecto_test.Printer()
    plasm.connect(add, "out", printer, "in")
    
    o = open('graph.dot', 'w')
    print >>o, plasm.viz()
    o.close()
    print "\n", plasm.viz(), "\n"
    sched = ecto.schedulers.Threadpool(plasm)
    sched.execute(1, 5)
    sched.execute(1, 5)
    return
    # print add.outputs
    result = add.outputs.out
    print "result=", result
    assert(result == 6.0)
    print "first iteration okay"
    sched.execute(1, 1)
    result = add.outputs.out
    print "iter2 result=", result
    assert result == 22.0


if __name__ == '__main__':
    test_plasm()



